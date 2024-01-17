package uk.co.roteala.glaciernode.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.co.roteala.common.storage.StorageTypes;
import uk.co.roteala.glaciernode.configs.NodeConfigs;
import uk.co.roteala.net.ConnectionsStorage;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerInitializer extends AbstractVerticle {
    @Autowired
    private NodeConfigs nodeConfigs;

    private final ConnectionsStorage connectionsStorage;

    private final ServerTransmissionHandler serverTransmissionHandler;

    @Override
    public void start(Promise<Void> startPromise) {
        NetServerOptions options = new NetServerOptions();
        options.setPort(7331);
        options.setHost(nodeConfigs.getNodeServerIP());

        NetServer server = vertx.createNetServer(options);

        server.connectHandler(new SocketConnectionHandler());

        server.listen(result -> {
            if (result.succeeded()) {
                log.info("Node server listening on: {} port: {}",nodeConfigs.getNodeServerIP(), server.actualPort());
                startPromise.complete();
            }
        });
    }

    private class SocketConnectionHandler implements Handler<NetSocket> {
        @Override
        public void handle(NetSocket socket) {
            log.info("New connection from: {}", socket.remoteAddress().hostAddress());

            connectionsStorage.getAsServerConnections().add(socket);

            socket.handler(serverTransmissionHandler.processWithConnection(socket));

            socket.closeHandler(close -> {
                log.info("Node: {} disconnected!", socket.remoteAddress().hostAddress());

                connectionsStorage.getAsServerConnections()
                        .remove(socket);
            });
        }
    }
}
