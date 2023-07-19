package uk.co.roteala.glaciernode.p2p;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.util.List;

/**
 * Handles all the connection between the peers
 * */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PeerGroupConnections {
    private List<Connection> connections;

    private boolean networkMode = true;

//    @Bean
//    public List<Connection> getConnections() throws RocksDBException {
//        int nb = 0;
//        log.info("===START CREATING P2P CONNECTIONS===");
//        Integer numberPeers = storage.getPeersFromStorage().size();
//
//        TcpClient tcpClient = TcpClient.create();
//            Flux.fromIterable(storage.getPeersFromStorage())
//                    .flatMap(peer -> {
//                        if(peer.isActive()){
//                                return tcpClient.host(peer.getAddress())
//                                        .port(networkMode ? 7331 : peer.getPort())
//                                        .doOnConnect(c -> log.info("Trying to connect to..."))
//                                        .doOnConnected(c -> {
//                                            log.info("Connection created successfully with:{}", peer.getAddress());
//                                            connections.add(c);
//                                        })
//                                        .doOnDisconnected(c -> log.info("Connection disrupted"))
//                                        .connect()
//                                        .doOnSuccess(connection -> connections.add(connection))
//                                        .doOnError(throwable -> {
//                                            log.info("Failed to connect...");
//                                            storage.updatePeerStatus(peer, false);
//                                        })
//                                        .thenReturn(peer.getAddress())
//                                        .onErrorResume(throwable -> Mono.empty());
//                        }
//                        return Mono.empty();
//                    }).subscribe();
//        log.info("===CONNECTIONS CREATED({})===", nb);
//        return connections;
//    }
}
