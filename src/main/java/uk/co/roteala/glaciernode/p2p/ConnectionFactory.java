package uk.co.roteala.glaciernode.p2p;

import org.springframework.context.annotation.Configuration;
import reactor.netty.Connection;
import uk.co.roteala.net.Peer;

import java.util.List;

/**
 * Handles all the connection between the peers
 * */
@Configuration
public class ConnectionFactory {
    private List<Connection> connections;

    public List<Connection> getConnections() {
        return connections;
    }

    private void createConnection(Peer peer){}
}
