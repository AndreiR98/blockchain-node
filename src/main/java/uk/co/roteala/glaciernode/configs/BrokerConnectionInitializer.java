package uk.co.roteala.glaciernode.configs;

import io.netty.channel.ChannelOption;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import uk.co.roteala.common.messenger.MessengerUtils;
import uk.co.roteala.glaciernode.handlers.BrokerTransmissionHandler;
import uk.co.roteala.glaciernode.storage.Storages;
import uk.co.roteala.net.ConnectionsStorage;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerConnectionInitializer {
    private final BrokerTransmissionHandler brokerTransmissionHandler;

    @Bean
    public void brokerConnection() {
        Vertx vertx = Vertx.vertx();
        NetClientOptions options = new NetClientOptions().setConnectTimeout(10000)
                .setTcpKeepAlive(true);
        NetClient client = vertx.createNetClient(options);

        client.connect(7331, "blockchain-broker-service.default.svc.cluster.local", brokerTransmissionHandler);
    }
}
