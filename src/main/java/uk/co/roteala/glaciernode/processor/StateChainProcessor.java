package uk.co.roteala.glaciernode.processor;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import uk.co.roteala.common.BlockHeader;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.NodeState;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.events.MessageWrapper;
import uk.co.roteala.common.events.ValidationType;
import uk.co.roteala.exceptions.SyncException;
import uk.co.roteala.exceptions.errorcodes.SyncErrorCode;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.PeersConnectionFactory;
import uk.co.roteala.glaciernode.storage.StorageServices;

import javax.swing.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
@NoArgsConstructor
public class StateChainProcessor {

    @Autowired
    private ClientConnectionStorage clientConnectionStorage;

    @Autowired
    private StorageServices storage;

    @Autowired
    private PeersConnectionFactory peersConnectionFactory;

    @Autowired
    private MiningWorker worker;

    private ChainState currentState;

    private Connection brokerConnection;
    /**
     * Add a new state chain and create node state
     * */
    public void processAppend() {
        try {
            this.storage.addStateTrie(this.currentState);
            log.info("New state added:{}", this.currentState);

            NodeState nodeState = new NodeState();
            nodeState.setUpdatedAt(System.currentTimeMillis());
            nodeState.setLastBlockIndex(0);
            nodeState.setRemainingBlocks(this.currentState.getLastBlockIndex());

            this.worker.setHasStateSync(true);

            if(this.currentState.getLastBlockIndex() == 0) {
                this.worker.setHasDataSync(true);
            }

            this.storage.addNodeState(nodeState);
            log.info("New node state created:{}", nodeState);

            log.info("Miner status:{}", this.worker);

            //Handles peers logic
            peersConnections(nodeState);

        } catch (Exception e) {
            log.error("Error while processing chain state:{}", e.getMessage());
        }
    }

    /**
     * Modify the existing state chain and node state
     * */
    public void processModify() {
        try {
            ChainState localChainState = this.storage.getStateTrie();
            NodeState nodeState = this.storage.getNodeState();

            int remainingBlocks = this.currentState.getLastBlockIndex() - nodeState.getLastBlockIndex();

            nodeState.setUpdatedAt(System.currentTimeMillis());
            nodeState.setRemainingBlocks(remainingBlocks);
            //nodeState.setLastBlockIndex(localChainState.getLastBlockIndex());

            this.worker.setHasStateSync(true);

            if(remainingBlocks == 0) {
                this.worker.setHasDataSync(true);
            }

            this.storage.updateStateTrie(this.currentState);
            log.info("State chain updated:{}", this.currentState);

            this.storage.updateNodeState(nodeState);
            log.info("Node state updated:{}", nodeState);

            log.info("Miner status:{}", this.worker);

            //Handles peer logic
            peersConnections(nodeState);

        } catch (Exception e) {
            log.error("Error while modify chain state:{}", e.getMessage());
        }
    }


    /**
     * Fully sync the node
     * */

    /**
     * Execute peers connections as soon as we have connections inside the storage
     * If no connections available then we request from broker and process them in a different way
     * */
    private void peersConnections(NodeState nodeState) {
        try {
            /**
             * Check if we have peers inside storage and try to connect to them, if fails ask broker for connections
             * */
            if(!this.storage.getPeersFromStorage().isEmpty()) {
                Flux.fromIterable(this.storage.getPeersFromStorage())
                        .concatMap(peer -> {
                            log.info("===== CONNECTION FACTORY =====");
                            return this.peersConnectionFactory.createConnection(peer)
                                    .then(Mono.just(peer))
                                    .delayElement(Duration.ofMillis(1000));
                        })
                        .doOnComplete(() -> {
                            /**
                             * Count how many success connections we achieved
                             * */
                            int numberOfConnections = 0;
                            log.info("Connections:{}", this.clientConnectionStorage.getClientConnections());
                            if(this.clientConnectionStorage.getClientConnections() != null){
                                numberOfConnections = this.clientConnectionStorage.getClientConnections().size();
                            }
                            if(numberOfConnections > 0) {
                                //execute partitioner
                                //partitioner();
                                this.worker.setHasParents(true);
                            } else {
                                MessageWrapper peersRequest = new MessageWrapper();
                                peersRequest.setVerified(ValidationType.TRUE);
                                peersRequest.setType(MessageTypes.PEERS);
                                peersRequest.setContent(nodeState);//Send node state
                                peersRequest.setAction(MessageActions.EMPTY_PEERS);

                                log.info("No connections available request peers from broker!");
                                this.brokerConnection.outbound()
                                        .sendObject(peersRequest.serialize())
                                        .then().subscribe();
                            }
                            log.info("===== CONNECTION FACTORY ENDED =====");
                        })
                        .then().subscribe();
            } else {
                MessageWrapper peersRequest = new MessageWrapper();
                peersRequest.setVerified(ValidationType.TRUE);
                peersRequest.setType(MessageTypes.PEERS);
                peersRequest.setContent(nodeState);//Send node state
                peersRequest.setAction(MessageActions.EMPTY_PEERS);

                log.info("No connections available request peers from broker!");
                this.brokerConnection.outbound()
                        .sendObject(peersRequest.serialize())
                        .then().subscribe();
            }
        } catch (Exception e) {
            log.error("Error while processing peers connections requests:{}", e.getMessage());
        }
    }

    /**
     * Partition the amount of workload between all connected peers(only parents)
     * Compute the remaining blocks we have left and send request to each peer a fixed number of blocks
     * In the case we can't send a fixed number of locks we choose randomly peers that gets more workload
     * Example:
     *      Remaining blocks: 17, 3 connected peers
     *      17 % 3 = 5 fixed number of blocks and 2 more blocks that are going to be distributed amount remaining peers
     *      Node will send block request with only the index REQUEST action and BLOCK type
     *      Others will respond with REQUEST_SYNC, BLOCK Type and as content, block without transaction list
     *      If block has no transaction(check markle root/nb of transaction) then we append the block
     *      If the has transactions then we receive the transactions first then the block and we re-construct the block.
     * */
    private void partitioner() {
        try {
            List<Integer> remainingBlocksList = new ArrayList<>();

            NodeState nodeState = this.storage.getNodeState();

            final int remainingBlocks = nodeState.getRemainingBlocks();

            if(this.clientConnectionStorage.getClientConnections().isEmpty()) {
                throw new SyncException(SyncErrorCode.NO_CONNECTIONS);
            }

            int numberOfConnection = this.clientConnectionStorage.getClientConnections().size();

            final int fixedNumberPerPeer = remainingBlocks % numberOfConnection;
            final int additionalBlocks = remainingBlocks - (fixedNumberPerPeer * numberOfConnection);

            int currentIndex = nodeState.getLastBlockIndex();

            int requestedBlocks = 0;
            int currentConnectionIndex = 0;
            do {
                for(int i = currentIndex; i < (currentIndex + fixedNumberPerPeer); i++) {
                    try {
                        MessageWrapper blockRequestWrapper = new MessageWrapper();
                        blockRequestWrapper.setAction(MessageActions.REQUEST);
                        blockRequestWrapper.setType(MessageTypes.BLOCKHEADER);
                        blockRequestWrapper.setVerified(ValidationType.TRUE);
                        blockRequestWrapper.setContent(BlockHeader.builder()
                                        .index(i)
                                .build());

                        this.clientConnectionStorage.getClientConnections()
                                .get(currentConnectionIndex)
                                .outbound().sendObject(Mono.just(blockRequestWrapper.serialize()))
                                .then().delayElement(Duration.ofMillis(150))
                                .subscribe();
                    } catch (Exception e) {
                        //reprocess that chunk
//                        int reprocessedBlocks = 0;
//                        do {
//
//                        } while(reprocessedBlocks == fixedNumberPerPeer);
                    }
                }
                currentIndex += fixedNumberPerPeer;

                requestedBlocks += fixedNumberPerPeer;
                currentConnectionIndex++;
            }while((requestedBlocks + fixedNumberPerPeer) <= remainingBlocks);

            for(int i = nodeState.getLastBlockIndex(); i < remainingBlocks; i++) {
                remainingBlocksList.add(i);
            }


        } catch (Exception e) {
            log.error("Error while partitioning the remaining blocks!");
        }
    }
}
