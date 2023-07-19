package uk.co.roteala.glaciernode.configs;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.common.events.ChainStateMessage;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.glaciernode.handlers.BrokerConnectionHandler;
import uk.co.roteala.glaciernode.miner.Miner;
import uk.co.roteala.glaciernode.miner.Mining;
import uk.co.roteala.glaciernode.p2p.PeerGroupConnections;
import uk.co.roteala.glaciernode.p2p.PeersConnections;
import uk.co.roteala.glaciernode.processor.MessageProcessor;
import uk.co.roteala.glaciernode.processor.Processor;
import uk.co.roteala.glaciernode.services.MoveBalanceExecutionService;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NodeConfig {
    private final StorageServices storage;

    private List<Connection> connections = new ArrayList<>();

    @Bean
    public void startBrokerConnection() {
        TcpClient.create()
                .host("crawler-dns.default.svc.cluster.local")
                //.host("92.88.73.211")
                .option(ChannelOption.SO_KEEPALIVE, true)
                .port(7331)
                .handle(brokerConnectionHandler())
                .doOnConnected(connection -> {
                    log.info("Connection to broker established!");
                    requestSyncFromBroker(connection);
                })
                .doOnDisconnected(c -> log.info("Connection to broker disrupted!"))
                .connect()
                .subscribe();
    }

    @Bean
    public void startServer() {
        TcpServer.create()
                .doOnBound(server -> log.info("Server started on address:{} and port:{}", server.address(), server.port()))
                .doOnUnbound(server -> log.info("Server stopped!"))
                .port(7331)
                .bindNow()
                .onDispose();
    }

    public synchronized Connection requestSyncFromBroker(Connection connection) {
        Message syncState = new ChainStateMessage(null);
        syncState.setMessageAction(MessageActions.SYNC);

        connection.outbound().sendObject(Mono
                        .just(Unpooled.copiedBuffer(SerializationUtils.serialize(syncState))))
                .then().subscribe();
       return connection;
    }

    @Bean
    public Consumer<Connection> connectionStorageHandler() {
        return connection -> {
            this.connections.add(connection);
        };
    }

    @Bean
    public PeerGroupConnections peerGroupConnections() {
        return new PeerGroupConnections();
    }

    /**
     * Handles broker communication
     * */
    @Bean
    public BrokerConnectionHandler brokerConnectionHandler() {
        return new BrokerConnectionHandler(storage);
    }

    /**
     * Update account balances
     * */
    @Bean
    public MoveFund moveFundExecution() {
        return new MoveBalanceExecutionService(storage);
    }

    /**
     * Process messages from broker or other nodes
     * */
    @Bean
    public Processor messageProcessor() {
        return new MessageProcessor(storage);
    }

    @Bean
    public Mining miner() {
        return new Miner(storage, messageProcessor());
    }

    /**
     * Handles all peers connections, when we receive a new peers from the broker we create a new connection
     * */
//    @Bean
//    public PeersConnections peersConnections() {
//        return new PeersConnections(storage);
//    }
}
