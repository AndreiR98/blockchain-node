package uk.co.roteala.glaciernode.p2p;

import io.netty.channel.nio.NioEventLoopGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.net.Peer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles all the connection between the peers
 * */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ConnectionFactory {

    private final StorageServices storage;
    private List<Connection> connections;

    @Bean
    public List<Connection> getConnections() throws RocksDBException {
        int nb = 0;
        log.info("===START CREATING P2P CONNECTIONS===");
        Integer numberPeers = storage.getPeersFromStorage().size();

        if(numberPeers > 50) {
            do {
                for (Peer peer : storage.getPeersFromStorage()) {
                    if(peer.isActive()){
                        createConnection(peer);
                    }
                    nb++;
                }
            } while (nb <= 49);
        } else {
            for (Peer peer : storage.getPeersFromStorage()) {
                if(peer.isActive()){
                    createConnection(peer);
                }
            }
        }


        log.info("===CONNECTIONS CREATED({})===", nb);
        return connections;
    }

    private void createConnection(Peer peer){

        AtomicInteger connectionAttempts = new AtomicInteger();

        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();

        TcpClient tcpClient = TcpClient.create()
                .host(peer.getAddress())
                .port(7331)
                .doOnConnect(c -> log.info("Connection created successfully with:{}", peer.getAddress()))
                .doOnConnected(c -> connections.add(c))
                .doOnDisconnected(c -> log.info("Connection disrupted"));

        Mono<? extends Connection> connectionMono = tcpClient.connect();

        connectionMono.subscribe(null, throwable -> {
            if(connectionAttempts.get() < 2) {
                connectionAttempts.getAndIncrement();
                connectionMono.subscribe();
            } else {
                eventLoopGroup.shutdownGracefully();
            }
        });
        try{
            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception e) {
            log.info("Failed to connect with:{}", peer.getAddress());
        } finally {
            storage.updatePeerStatus(peer, false);
            eventLoopGroup.shutdownGracefully();
        }
    }
}
