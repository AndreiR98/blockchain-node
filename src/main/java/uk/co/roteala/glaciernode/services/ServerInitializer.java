package uk.co.roteala.glaciernode.services;

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
import uk.co.roteala.glaciernode.configs.NodeConfigs;
import uk.co.roteala.net.ConnectionsStorage;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerInitializer extends AbstractVerticle {
    @Autowired
    private NodeConfigs nodeConfigs;

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
        public void handle(NetSocket event) {

        }
    }
}
