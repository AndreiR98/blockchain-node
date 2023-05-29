package uk.co.roteala.glaciernode.p2p;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.net.Peer;

import java.util.List;

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
        try {
            TcpClient.create()
                    .host(peer.getAddress())
                    .port(peer.getPort() - 1)
                    .doOnConnect(c -> log.info("Connection created successfully with:{}", peer.getAddress()))
                    .doOnConnected(c -> connections.add(c))
                    .doOnDisconnected(c -> log.info("Connection disrupted"))
                    .connectNow();
        } catch (Exception e) {
            storage.updatePeerStatus(peer, false);
            log.info("Failed to connect with:{}", peer.getAddress());
        }
    }
}
