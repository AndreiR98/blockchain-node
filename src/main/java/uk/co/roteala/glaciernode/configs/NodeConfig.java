package uk.co.roteala.glaciernode.configs;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.common.events.ChainStateMessage;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.glaciernode.handlers.BrokerTransmissionHandler;
import uk.co.roteala.glaciernode.p2p.BrokerConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
import uk.co.roteala.glaciernode.processor.BrokerMessageProcessor;
import uk.co.roteala.glaciernode.services.MoveBalanceExecutionService;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NodeConfig {
    private final StorageServices storage;

    private final GlacierConfigs config;

    @Bean
    public void startBrokerConnection() {
        Supplier<SocketAddress> localAddressSupplier = () -> new InetSocketAddress(config.getNodeServerIP(), 7332);

        TcpClient.create()
                .host("crawler-dns.default.svc.cluster.local")
                .option(ChannelOption.SO_KEEPALIVE, true)
                //.host("localhost")
                .port(7331)
                //.bindAddress(localAddressSupplier)
                .handle(brokerTransmissionHandler())
                .doOnConnected(requestSyncFromBroker())
                .doOnDisconnected(c -> log.info("Connection to broker disrupted!"))
                .connect()
                .subscribe();
    }

    @Bean
    public Consumer<Connection> requestSyncFromBroker() {
        return connection -> {
            log.info("Connection to broker started!");

            Message syncState = new ChainStateMessage();
            syncState.setMessageAction(MessageActions.REQUEST_SYNC);

            connection.outbound().sendObject(Mono
                            .just(Unpooled.copiedBuffer(SerializationUtils.serialize(syncState))))
                    .then().subscribe();
        };
    }

    @Bean
    public void startServer() {
        TcpServer.create()
                .host(config.getNodeServerIP())
                .doOnConnection(serverConnectionStorage())
                .doOnBound(server -> log.info("Server started on address:{} and port:{}", server.address(), config.getNodeServerIP()))
                .doOnUnbound(server -> log.info("Server stopped!"))
                //.port(7331)
                .bindNow()
                .onDispose();
    }

    /**
     * Keeps track of all incoming clients connections
     * */
    @Bean
    public ServerConnectionStorage serverConnectionStorage() {
        return new ServerConnectionStorage();
    }

    /**
     * Keeps track of broker connection
     * */
    @Bean
    public BrokerConnectionStorage brokerConnectionStorage() {
        return new BrokerConnectionStorage();
    }

    /**
     * Keeps track of servers connections
     * */
    @Bean
    public ClientConnectionStorage clientConnectionStorage() {
        return new ClientConnectionStorage();
    }

    @Bean
    public BrokerMessageProcessor brokerMessageProcessor() {
        return new BrokerMessageProcessor();
    }

    @Bean
    public BrokerTransmissionHandler brokerTransmissionHandler() {
        return new BrokerTransmissionHandler(brokerMessageProcessor());
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


    //@Bean
//    public Mining miner() {
//        return new Miner(storage);
//    }
}
