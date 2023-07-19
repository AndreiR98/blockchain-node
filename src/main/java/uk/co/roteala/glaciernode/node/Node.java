package uk.co.roteala.glaciernode.node;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.glaciernode.handlers.BrokerConnectionHandler;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Node {
    private final StorageServices storage;
    private final List<Connection> connections;

    private boolean networkMode = true;
    @Bean
    public void startNode() throws RocksDBException {
        //TODO:Move to storage section
        //setGenesis();

        //OK
        //startServer();

        //Move the seeder in here too
        startConnectionFactory();

    }
    private void startServer(){
        TcpServer.create()
                .port(7331)
                .doOnConnection(c -> log.info("Connection received from:{}", c.address()))
                .option(ChannelOption.SO_KEEPALIVE, true)
                .bind()
                .doOnSuccess(s -> log.info("Server started on:{}!", s.port()))
                .subscribe();
    }

    private void startConnectionFactory() throws RocksDBException {
        //seederConnection();
        if (this.connections.size() < 5){
                connectionFactory();
        }
    }

    private void connectionFactory() throws RocksDBException {
        log.info("===START CREATING P2P CONNECTIONS===");

        TcpClient tcpClient = TcpClient.create();

        Flux.fromIterable(storage.getPeersFromStorage())
                .publishOn(Schedulers.boundedElastic())
                .flatMap(peer -> {
                    if(peer.isActive()){;
                        return tcpClient.host(peer.getAddress())
                                .port(networkMode ? 7331 : peer.getPort())
                                .doOnConnect(c -> log.info("Trying to connect to..."))
                                .doOnConnected(c -> {
                                    log.info("Connection created successfully with:{}", peer.getAddress());
                                    //connections.add(c);
                                })
                                .doOnDisconnected(c -> {
                                    this.connections.remove(c);
                                    log.info("Connection disrupted");
                                })
                                .connect()
                                .doOnSuccess(this.connections::add)
                                .doOnError(throwable -> {
                                    log.info("Failed to connect...");
                                    storage.updatePeerStatus(peer, false);
                                })
                                .thenReturn(peer.getAddress())
                                .onErrorResume(throwable -> Mono.empty());
                    }
                    return Mono.empty();
                }).subscribe();

        log.info("===CONNECTIONS CREATED===");
    }

//    public void seederConnection() {
//        TcpClient.create()
//                .host("3.8.86.130")
//                //.host("92.88.73.211")
//                .option(ChannelOption.SO_KEEPALIVE, true)
//                .port(7331)
//                .handle(seederHandler())
//                .doOnConnect(c -> log.info("Connection to seeder established!"))
//                .doOnDisconnected(c -> log.info("Connection to seeder disrupted!"))
//                .connect()
//                .subscribe();
//    }
//    @Bean
//    public BrokerConnectionHandler seederHandler() {
//        return new BrokerConnectionHandler(storage);
//    }

//    @Bean
//    public void setGenesis() throws Exception {
//        BaseBlockModel block = new BaseBlockModel();
//        block.setVersion(0x10);
//        block.setMarkleRoot("51d0be80dc114734d9e9727db5f04b3fe3cf54a9b168a780e43c9867120edc56");
//        block.setTimeStamp(1685717651791L);
//        block.setNonce(new BigInteger("2365481254789655550"));
//        block.setPreviousHash("0000000000000000000000000000000000000000000000000000000000000000");
//        block.setNumberOfBits(957);
//        block.setDifficulty(new BigInteger("1"));
//        block.setTransactions(List.of("51d0be80dc114734d9e9727db5f04b3fe3cf54a9b168a780e43c9867120edc56"));
//        block.setIndex(0);
//        block.setHash("d0bdfc7e6ede0d0dca9724909feb0f2a767f0f9780a6b1e7282709b4c58aaeef");
//        block.setMiner("16wRoKZXiETVRKvuucy4NFnYbiiKZ8ZUSx");
//        block.setConfirmations(1);
//        block.setStatus(BlockStatus.MINED);
//
//        UTXO in = new UTXO();
//        in.setCoinbase(true);
//        in.setAddress(null);
//        in.setValue(null);
//        in.setPubKeyScript("b20d4f2141768c75c59fa74291b0034288d913d601e2dc8ec678abdafa8be4de");
//        in.setSigScript(null);
//        in.setTxid("0000000000000000000000000000000000000000000000000000000000000000");
//
//        UTXO out = new UTXO();
//        out.setCoinbase(true);
//        out.setAddress("16wRoKZXiETVRKvuucy4NFnYbiiKZ8ZUSx");
//        out.setPubKeyScript("b20d4f2141768c75c59fa74291b0034288d913d601e2dc8ec678abdafa8be4de");
//        out.setValue(Coin.valueOf(100));
//        out.setSpender(null);
//        out.setSpent(false);
//        out.setTxid("51d0be80dc114734d9e9727db5f04b3fe3cf54a9b168a780e43c9867120edc56");
//
//        TransactionBaseModel tx = new TransactionBaseModel();
//        tx.setHash("51d0be80dc114734d9e9727db5f04b3fe3cf54a9b168a780e43c9867120edc56");
//        tx.setBlockHash("d0bdfc7e6ede0d0dca9724909feb0f2a767f0f9780a6b1e7282709b4c58aaeef");
//        tx.setBlockNumber(0);
//        tx.setFees(Coin.ZERO);
//        tx.setVersion(0x10);
//        tx.setTransactionIndex(0);
//        tx.setTimeStamp(1685717651791L);
//        tx.setConfirmations(1);
//        tx.setBlockNumber(0);
//        tx.setStatus(TransactionStatus.SUCCESS);
//
//        //storage.addBlock(block.getHash(), block);
//        //storage.addTransaction(tx.getHash(), tx);
//        //storage.flush();
//    }
}
