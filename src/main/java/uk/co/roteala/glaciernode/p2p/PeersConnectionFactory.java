package uk.co.roteala.glaciernode.p2p;

import com.rometools.utils.Integers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpClient;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.*;
import uk.co.roteala.glaciernode.handlers.ClientTransmissionHandler;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.net.Peer;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PeersConnectionFactory {

    private final StorageServices storage;

    @Autowired
    private ClientConnectionStorage clientConnectionStorage;

    @Autowired
    private ClientTransmissionHandler clientTransmissionHandler;

    /**
     * Initialize connections with peers or ask broker for that
     * */
    public void createConnections(Message message) {
        PeersContainer container = (PeersContainer) message.getMessage();
        Connection connection = message.getConnection();
        NodeState nodeState = storage.getNodeState();

        ChainState state = storage.getStateTrie();
        state.setGenesisBlock(null);
        state.setAccounts(null);
        state.setReward(null);
        state.setTarget(null);
        state.setNetworkFees(null);


        /**
         * Partition the missing blocks based on the number of peers
         * */
        if(nodeState.getRemainingBlocks() != 0) {
            //If remaning blocks == 0 it means we are synced request only mempool transactions

            Integer numberOfConnections = clientConnectionStorage.getClientConnections().size();

            List<Connection> clientConnectionsCopy = new ArrayList<>(clientConnectionStorage.getClientConnections());
            Collections.shuffle(clientConnectionsCopy);

            //Assign blocks to random peers
            if(numberOfConnections >= nodeState.getRemainingBlocks()) {

                List<Connection> partitionedRandomConnections = clientConnectionsCopy
                        .subList(0, nodeState.getRemainingBlocks());

                Integer oldIndex = state.getLastBlockIndex() - nodeState.getRemainingBlocks();

                for(int i = oldIndex; i <= state.getLastBlockIndex(); i++) {
                    int connectionIndex = 0;

                    Message preparedMessage = new BlockMessage(BlockHeader.builder()
                            .index(i)
                            .build());
                    preparedMessage.setVerified(true);
                    preparedMessage.setMessageAction(MessageActions.REQUEST);

                    partitionedRandomConnections.get(connectionIndex)
                            .outbound().sendObject(Mono.just(
                                    Unpooled.copiedBuffer(SerializationUtils.serialize(preparedMessage))))
                            .then().subscribe();

                    connectionIndex++;
                }

            } else {
                Integer numberOfBlocksPerPeer = nodeState.getRemainingBlocks() % numberOfConnections;
                Integer remainderOfBlocks = nodeState.getRemainingBlocks() - (numberOfBlocksPerPeer * numberOfConnections);
                Integer oldIndex = state.getLastBlockIndex() - nodeState.getRemainingBlocks();
                List<Integer> blockIndexes = new ArrayList<>();


                for(int i = oldIndex; i <= state.getLastBlockIndex(); i++) {
                    blockIndexes.add(i);
                }

                List<Integer> remaindersBlockIndexes = blockIndexes
                        .subList((numberOfBlocksPerPeer * numberOfConnections) - 1, blockIndexes.size());


                for(int i = 0; i < numberOfConnections; i++) {
                    int numbersOfLucky = 0;

                    List<Integer> blocksPerConnection = blockIndexes
                            .subList((i * numberOfBlocksPerPeer), 2*(numberOfBlocksPerPeer - 1));

                    if(this.isLucky() && numbersOfLucky <= remainderOfBlocks){
                        blocksPerConnection.add(remaindersBlockIndexes.get(numbersOfLucky));
                        numbersOfLucky++;
                    }

                    int finalI = i;
                    Flux.fromIterable(blocksPerConnection)
                                    .doOnNext(index -> {
                                      Message blockMessageRequest = new BlockMessage(BlockHeader.builder()
                                              .index(index)
                                              .build());
                                      blockMessageRequest.setMessageAction(MessageActions.REQUEST);

                                      clientConnectionsCopy
                                              .get(finalI).outbound()
                                              .sendObject(Mono.just(
                                                      Unpooled.copiedBuffer(SerializationUtils.serialize(blockMessageRequest))))
                                              .then();
                                    }).then().subscribe();
                }
            }
        }
    }

    private boolean isLucky() {
        Random random = new Random();

        return random.nextBoolean();
    }

    public Connection createConnection(Peer peer) {
        return TcpClient.create()
                .host(peer.getAddress())
                .port(7331)
                .doOnConnected(c -> log.info("Peer connected!"))
                .handle(clientTransmissionHandler)
                .doOnDisconnected(connection -> clientConnectionStorage.getClientConnections().remove(connection))
                .connectNow();
    }
}
