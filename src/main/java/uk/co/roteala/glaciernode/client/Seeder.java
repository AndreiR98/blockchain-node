package uk.co.roteala.glaciernode.client;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.common.BaseBlockModel;
import uk.co.roteala.common.BaseEmptyModel;
import uk.co.roteala.common.TransactionBaseModel;
import uk.co.roteala.glaciernode.handlers.SeederHandler;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.net.Peer;


/**
 * Seeder class that handles all the connections between peers
 * */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class Seeder {
    private final StorageServices storage;

    //private GlacierBrokerConfigs configs;

    @Bean
    public void seederConnection() {
        TcpClient.create()
                .host("3.8.86.130")
                .option(ChannelOption.SO_KEEPALIVE, true)
                .port(7331)
                .handle(seederHandler())
                .doOnConnect(c -> log.info("Connection to seeder established!"))
                .doOnDisconnected(c -> log.info("Connection to seeder disrupted!"))
                .connectNow();
    }

//    @Bean
//    public void seederServerTest() {
//        DisposableServer server = TcpServer.create()
//                .doOnConnection(c -> log.info("Connection"))
//                .option(ChannelOption.SO_KEEPALIVE, true)
//                .doOnBound(s -> {
//                    log.info("Server started on address:{} and port:{}", s.address(), s.port());
//                })
//                .doOnUnbound(ss -> {
//                    log.info("Server stopped!");
//                })
//                .bindNow();
//
//        log.info("Server:{}", server.port());
//    }
    @Bean
    public SeederHandler seederHandler() {
        return new SeederHandler(storage);
    }
}
