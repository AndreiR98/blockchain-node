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
import uk.co.roteala.glaciernode.handlers.ClientTransmissionHandler;
import uk.co.roteala.glaciernode.handlers.ServerTransmissionHandler;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.*;
import uk.co.roteala.glaciernode.processor.BrokerMessageProcessor;
import uk.co.roteala.glaciernode.processor.ClientMessageProcessor;
import uk.co.roteala.glaciernode.processor.ServerMessageProcessor;
import uk.co.roteala.glaciernode.services.MoveBalanceExecutionService;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NodeConfig {
    private final StorageServices storage;

    private final GlacierConfigs config;

    @Bean
    public MiningWorker minerWorker() {
        return new MiningWorker();
    }

    @Bean
    public Supplier<SocketAddress> addressSupplier() {
        return () -> new InetSocketAddress(config.getNodeServerIP(), 0);
    }


    @Bean
    public void startBrokerConnection() {
        TcpClient.create()
                //.host("crawler-dns.default.svc.cluster.local")
                //.host("3.8.20.9")
                //.bindAddress(addressSupplier())
                .host("3.10.246.30")
                .option(ChannelOption.SO_KEEPALIVE, true)
                .port(7331)
                .wiretap(true)
                .doOnConnected(brokerConnectionStorage())
                .handle(brokerTransmissionHandler())
                .doOnDisconnected(c -> {
                    this.minerWorker().setBrokerConnected(false);
                    log.info("Connection to broker disrupted!");
                })
                .connect()
                .subscribe();
    }

    //@Bean
    public Mono<Void> startServer() {
        return TcpServer.create()
                .port(7331)
                .option(ChannelOption.SO_KEEPALIVE, true)
                //.host(config.getNodeServerIP())
                .handle(serverTransmissionHandler())
                .doOnBound(server -> log.info("Server started on address:{} and port:{}", server.address(), server.port()))
                .doOnUnbound(server -> log.info("Server stopped!"))
                .bindNow()
                .onDispose();
    }

    @Bean
    public PeersConnectionFactory connectionFactory() {
        return new PeersConnectionFactory();
    }

    /**
     * Keeps track of all incoming clients connections
     * */
    @Bean
    public ServerConnectionStorage serverConnectionStorage() {
        return new ServerConnectionStorage();
    }

    @Bean
    public ServerTransmissionHandler serverTransmissionHandler() {
        return new ServerTransmissionHandler(serverMessageProcessor());
    }

    @Bean
    public ServerMessageProcessor serverMessageProcessor() {
        return new ServerMessageProcessor();
    }
    /**
     * Keeps track of servers connections
     * */
    @Bean
    public ClientConnectionStorage clientConnectionStorage() {
        return new ClientConnectionStorage();
    }

    @Bean
    public ClientTransmissionHandler clientTransmissionHandler() {
        return new ClientTransmissionHandler(clientMessageProcessor());
    }

    @Bean
    public ClientMessageProcessor clientMessageProcessor() {
        return new ClientMessageProcessor();
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
     * Keeps track of broker connection
     * */
    @Bean
    public BrokerConnectionStorage brokerConnectionStorage() {
        return new BrokerConnectionStorage(storage);
    }

    /**
     * Update account balances
     * */
    @Bean
    public MoveFund moveFundExecution() {
        return new MoveBalanceExecutionService(storage);
    }
}
