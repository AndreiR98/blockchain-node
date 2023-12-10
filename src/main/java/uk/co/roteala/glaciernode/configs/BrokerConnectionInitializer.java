package uk.co.roteala.glaciernode.configs;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.glaciernode.handlers.BrokerTransmissionHandler;
import uk.co.roteala.glaciernode.storage.Storages;
import uk.co.roteala.net.ConnectionsStorage;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerConnectionInitializer {
    @Autowired
    private final Storages storage;

    private final ConnectionsStorage connectionStorage;

    private final BrokerTransmissionHandler brokerTransmissionHandler;

    @Bean
    public void brokerConnection() {
        TcpClient.create()
                .host("localhost")
                .port(7331)
                .handle(brokerTransmissionHandler)
                .doOnConnected(connection -> {
                    log.info("Connection established with broker!");
                    this.connectionStorage.setBrokerConnection(connection);
                })
                .doOnDisconnected(connection -> {
                    log.info("Connection finished with broker!");
                    this.connectionStorage.setBrokerConnection(null);
                })
                .metrics(true)
                .connectNow();
    }
}
