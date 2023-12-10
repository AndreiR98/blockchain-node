//package uk.co.roteala.glaciernode.processor;
//
//import lombok.AllArgsConstructor;
//import lombok.NoArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//import reactor.netty.Connection;
//import uk.co.roteala.common.BlockHeader;
//import uk.co.roteala.common.ChainState;
//import uk.co.roteala.common.NodeState;
//import uk.co.roteala.common.events.MessageActions;
//import uk.co.roteala.common.events.MessageTypes;
//import uk.co.roteala.common.events.MessageWrapper;
//import uk.co.roteala.common.events.PeersContainer;
//import uk.co.roteala.glaciernode.miner.MiningWorker;
//import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
//import uk.co.roteala.glaciernode.p2p.PeersConnectionFactory;
//import uk.co.roteala.glaciernode.storage.StorageServices;
//import uk.co.roteala.net.Peer;
//
//import java.time.Duration;
//import java.util.ArrayList;
//import java.util.List;
//
//@Slf4j
//@Component
//@AllArgsConstructor
//@NoArgsConstructor
//public class PeersMessageProcessor {
//    @Autowired
//    private StorageServices storage;
//
//    @Autowired
//    private MiningWorker worker;
//
//    private Connection brokerConnection;
//
//    private PeersContainer container;
//
//    @Autowired
//    private PeersConnectionFactory peersConnectionFactory;
//
//    @Autowired
//    private ClientConnectionStorage clientConnectionStorage;
//
//    /**
//     *Get initial list of peers and try to connect to them
//     * */
//    public void processTryConnections() {
//        try {
//            List<Peer> peerList = new ArrayList<>();
//
//            if(!this.container.getPeersList().isEmpty()) {
//                for(String address : this.container.getPeersList()) {
//                    Peer peer = new Peer();
//                    peer.setActive(true);
//                    peer.setPort(7331);
//                    peer.setAddress(address);
//
//                    this.storage.addPeer(peer);
//                    peerList.add(peer);
//                }
//
//                NodeState nodeState = this.storage.getNodeState();
//
//                Flux.fromIterable(peerList)
//                        .concatMap(peer -> {
//                            log.info("===== CONNECTION FACTORY =====");
//                            return this.peersConnectionFactory.createConnection(peer)
//                                    .then(Mono.just(peer))
//                                    .delayElement(Duration.ofMillis(1000));
//                        })
//                        .doOnComplete(() -> {
//                            /**
//                             * Count how many success connections we achieved
//                             * */
//                            int numberOfConnections = 0;
//                            if(this.clientConnectionStorage.getClientConnections() != null){
//                                numberOfConnections = this.clientConnectionStorage.getClientConnections().size();
//                            }
//
//                            /**
//                             * Ask broker to sync
//                             * */
//                            if(numberOfConnections <= 0) {
//                                MessageWrapper peersRequest = new MessageWrapper();
//                                peersRequest.setType(MessageTypes.PEERS);
//                                peersRequest.setContent(nodeState);//Send node state
//                                peersRequest.setAction(MessageActions.NO_CONNECTIONS);
//
//                                log.info("No connections available request peers from broker!");
//                                this.brokerConnection.outbound()
//                                        .sendObject(peersRequest.serialize())
//                                        .then().subscribe();
//                            } else {
//                                this.worker.setHasParents(true);
//
//                                MessageWrapper messageWrapper = new MessageWrapper();
//                                messageWrapper.setType(MessageTypes.NODESTATE);
//                                messageWrapper.setContent(nodeState);
//                                messageWrapper.setAction(MessageActions.REQUEST_SYNC);
//
//                                for(Connection connection : this.clientConnectionStorage.getClientConnections()){
//
//                                    log.info("Require sync from:{}", connection);
//
//                                    connection.outbound()
//                                            .sendObject(messageWrapper.serialize())
//                                            .then().subscribe();
//                                }
//                            }
//                            log.info("===== CONNECTION FACTORY ENDED =====");
//                        })
//                        .then().subscribe();
//            }
//        } catch (Exception e) {
//            log.error("Failed to connect to peers!");
//        }
//    }
//
//    /**
//     * Try to form connections with the new peers
//     * */
//    //public void processTryConnections() {}
//
//    /**
//     * Process the remaining blocks
//     * */
//    public void processNoConnections() {
//        try {
//            NodeState nodeState = this.storage.getNodeState();
//
//            int currentNodeLastIndex = nodeState.getLastBlockIndex();
//
//            if(nodeState.getRemainingBlocks() > 0) {
//                Flux.range(currentNodeLastIndex+1, currentNodeLastIndex+nodeState.getRemainingBlocks())
//                        .map(blockIndex -> {
//                            BlockHeader blockHeader = new BlockHeader();
//                            blockHeader.setIndex(blockIndex);
//
//                            MessageWrapper messageWrapper = new MessageWrapper();
//                            messageWrapper.setAction(MessageActions.REQUEST);
//                            messageWrapper.setType(MessageTypes.BLOCKHEADER);
//                            messageWrapper.setContent(blockHeader);
//
//                            return messageWrapper;
//                        })
//                        .delayElements(Duration.ofMillis(150)).doOnNext(messageWrapper -> {
//                            this.brokerConnection.outbound().sendObject(Mono.just(messageWrapper.serialize()))
//                                    .then().subscribe();
//                        }).then().subscribe();
//            }
//
//        } catch (Exception e) {
//            log.error("Error during processing block requests!", e.getMessage());
//        }
//    }
//}
