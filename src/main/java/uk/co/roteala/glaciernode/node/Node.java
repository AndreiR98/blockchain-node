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
import uk.co.roteala.glaciernode.handlers.SeederHandler;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Node {
    private final StorageServices storage;
    private List<Connection> connections;

    private boolean networkMode = true;
    @Bean
    public void startNode() throws RocksDBException {
        startServer();

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
        if (!storage.getPeersFromStorage().isEmpty() && connections.size() < 5){
                connectionFactory();
        } else {
            //Retrieve the peers from seeder
            seederConnection();

            connectionFactory();
        }
    }

    private void connectionFactory() throws RocksDBException {
        log.info("===START CREATING P2P CONNECTIONS===");

        TcpClient tcpClient = TcpClient.create();

        Flux.fromIterable(storage.getPeersFromStorage())
                .publishOn(Schedulers.boundedElastic())
                .flatMap(peer -> {
                    if(peer.isActive()){
//                                Connection ca = tcpClient.host(peer.getAddress())
//                                        .port(networkMode ? 7331 : peer.getPort())
//                                        .doOnConnect(c -> log.info("Trying to connect to..."))
//                                        .doOnConnected(c -> {
//                                            log.info("Connection created successfully with:{}", peer.getAddress());
//                                        })
//                                        .doOnDisconnected(c -> {
//                                            connections.remove(c);
//                                            log.info("Connection disrupted");
//                                        })
//                                        .connect()
//                                        .doOnSuccess(connection -> connections.add(connection))
//                                        .doOnError(throwable -> {
//                                            log.info("Failed to connect...");
//                                            storage.updatePeerStatus(peer, false);
//                                        })
//                                        .onErrorResume(throwable -> Mono.empty())
//                                        .block();


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
                                .doOnSuccess(connection -> this.connections.add(connection))
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

    public void seederConnection() {
        TcpClient.create()
                .host("3.8.86.130")
                //.host("92.88.73.211")
                .option(ChannelOption.SO_KEEPALIVE, true)
                .port(7331)
                .handle(seederHandler())
                .doOnConnect(c -> log.info("Connection to seeder established!"))
                .doOnDisconnected(c -> log.info("Connection to seeder disrupted!"))
                .connect()
                .subscribe();
    }
    @Bean
    public SeederHandler seederHandler() {
        return new SeederHandler(storage);
    }
}
