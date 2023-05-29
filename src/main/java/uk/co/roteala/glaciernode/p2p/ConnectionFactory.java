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
        storage.getPeersFromStorage().forEach(this::createConnection);
        do {
            for (Peer peer : storage.getPeersFromStorage()) {
                createConnection(peer);
                nb++;
            }
        } while (nb <= 49);

        log.info("===CONNECTIONS CREATED({})===", nb);
        return connections;
    }

    private void createConnection(Peer peer){
        try {
            Connection connection = TcpClient.create()
                    .host(peer.getAddress())
                    .port(peer.getPort() - 1)
                    .doOnConnect(c -> log.info("Connection created sucessfully with:{}", peer.getAddress()))
                    .doOnDisconnected(c -> log.info("Connection disrupted"))
                    .connectNow();

            if(!connection.isDisposed()) {
                connections.add(connection);
            }
        } catch (Exception e) {
            //storage.deletePeer(peer);
            log.info("Failed to connect with:{}", peer.getAddress());
        }
    }
}
