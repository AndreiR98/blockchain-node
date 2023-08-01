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
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.*;
import uk.co.roteala.glaciernode.processor.BrokerMessageProcessor;
import uk.co.roteala.glaciernode.services.MoveBalanceExecutionService;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NodeConfig {
    private final StorageServices storage;

    private final GlacierConfigs config;

    @Bean
    public MiningWorker canMine() {
        return new MiningWorker();
    }

    /**
     * Searches the peer store and creates connections, if empty then send a message to broker
     * When received from broker creates connections with them
     * Ask peers for more peers
     * */
    @Bean
    public void connectionFactory() {
        PeersConnectionFactory
                .storages(storage, clientConnectionStorage(), brokerConnectionStorage())
                .create();
    }

    @Bean
    public void startBrokerConnection() {
        TcpClient.create()
                //.host("crawler-dns.default.svc.cluster.local")
                //.host("86.238.249.2")
                .host("localhost")
                .option(ChannelOption.SO_KEEPALIVE, true)
                .port(7331)
                .doOnConnected(brokerConnectionStorage())
                .handle(brokerTransmissionHandler())
                .doOnConnected(c -> log.info("Connection to broker established!"))
                .doOnDisconnected(c -> log.info("Connection to broker disrupted!"))
                .connect()
                .subscribe();
    }

//    @Bean
//    public Consumer<Connection> requestSyncFromBroker() {
//        return connection -> {
//            log.info("Connection to broker started!");
//
//            Message syncState = new ChainStateMessage();
//            syncState.setMessageAction(MessageActions.REQUEST_SYNC);
//
//            connection.outbound().sendObject(Mono
//                            .just(Unpooled.copiedBuffer(SerializationUtils.serialize(syncState))))
//                    .then().subscribe();
//        };
//    }

    //@Bean
    public void startServer() {
        TcpServer.create()
                //.host(config.getNodeServerIP())
                .doOnConnection(serverConnectionStorage())
                .doOnBound(server -> log.info("Server started on address:{} and port:{}", server.address(), config.getNodeServerIP()))
                .doOnUnbound(server -> log.info("Server stopped!"))
                .port(7331)
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
