package uk.co.roteala.glaciernode.client;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.co.roteala.net.ConnectionsStorage;
import uk.co.roteala.net.Peer;

import static uk.co.roteala.glaciernode.configs.NodeConfigs.DEFAULT_CONNECTION_ALLOWED;

@Service
public class ClientInitializer {
    @Autowired
    private ClientTransmissionHandler clientTransmissionHandler;

    public void connect(Peer peer) {
        Vertx vertx = Vertx.vertx();
        NetClientOptions options = new NetClientOptions().setConnectTimeout(10000)
                .setTcpKeepAlive(true);
        NetClient client = vertx.createNetClient(options);

        client.connect(peer.getPort(), peer.getAddress(), clientTransmissionHandler);
    }
}
