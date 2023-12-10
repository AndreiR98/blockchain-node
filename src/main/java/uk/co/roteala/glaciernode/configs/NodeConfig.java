package uk.co.roteala.glaciernode.configs;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.common.storage.ColumnFamilyTypes;
import uk.co.roteala.common.storage.StorageTypes;
import uk.co.roteala.core.Blockchain;
import uk.co.roteala.exceptions.StorageException;
import uk.co.roteala.exceptions.errorcodes.StorageErrorCode;
import uk.co.roteala.glaciernode.handlers.BrokerTransmissionHandler;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.*;
import uk.co.roteala.glaciernode.storage.Storages;
import uk.co.roteala.net.ConnectionsStorage;
import uk.co.roteala.utils.Constants;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NodeConfig {
    private final Storages storage;
    @Bean
    @DependsOn({
            "initializeStateTrieStorage",
            "initializeMempoolStorage",
            "initializeBlockchainStorage",
            "initializePeersStorage"
    })
    public void genesisConfig() {
        try {
            if(!storage.getStorage(StorageTypes.STATE)
                    .has(ColumnFamilyTypes.STATE, Constants.DEFAULT_STATE_NAME.getBytes(StandardCharsets.UTF_8))) {
                log.info("Creating new genesis state");
                Blockchain.initializeGenesisState(storage.getStorage(StorageTypes.STATE));
                Blockchain.initializeGenesisBlock(storage.getStorage(StorageTypes.BLOCKCHAIN));

                storage.getStorage(StorageTypes.BLOCKCHAIN)
                        .put(true, ColumnFamilyTypes.BLOCKS, "2".getBytes(), Constants.GENESIS_BLOCK);
            }
        } catch (Exception e) {
            log.error("Filed to initialize genesis state!", e);
            throw new StorageException(StorageErrorCode.STORAGE_FAILED);
        }
    }

    @Bean
    public ConnectionsStorage connectionsStorage() {
        return new ConnectionsStorage();
    }

    @Bean
    public BrokerTransmissionHandler brokerTransmissionHandler() {
        return new BrokerTransmissionHandler();
    }

    @Bean
    public MiningWorker minerWorker() {
        return new MiningWorker();
    }

    @Bean
    public AssemblerMessenger messageAssembler() {
        return new AssemblerMessenger();
    }

    @Bean
    public ExecutorMessenger executorMessenger() {
        return new ExecutorMessenger();
    }

    //@Bean
    public Mono<Void> startServer() {
        return TcpServer.create()
                .port(7331)
                .option(ChannelOption.SO_KEEPALIVE, true)
                //.host(config.getNodeServerIP())
                //.handle(serverTransmissionHandler())
                .doOnBound(server -> log.info("Server started on address:{} and port:{}", server.address(), server.port()))
                .doOnUnbound(server -> log.info("Server stopped!"))
                .bindNow()
                .onDispose();
    }

//    @Bean
//    public PeersConnectionFactory connectionFactory() {
//        return new PeersConnectionFactory();
//    }

    /**
     * Keeps track of all incoming clients connections
     * */
    @Bean
    public ServerConnectionStorage serverConnectionStorage() {
        return new ServerConnectionStorage();
    }


    /**
     * Keeps track of servers connections
     * */
//    @Bean
//    public ClientConnectionStorage clientConnectionStorage() {
//        return new ClientConnectionStorage();
//    }


    /**
     * Keeps track of broker connection
     * */
//    @Bean
//    public BrokerConnectionStorage brokerConnectionStorage() {
//        return new BrokerConnectionStorage(storage);
//    }

    /**
     * Update account balances
     * */
//    @Bean
//    public MoveFund moveFundExecution() {
//        return new MoveBalanceExecutionService(storage);
//    }
}
