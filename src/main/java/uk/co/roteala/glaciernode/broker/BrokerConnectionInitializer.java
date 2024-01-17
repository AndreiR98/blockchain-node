package uk.co.roteala.glaciernode.broker;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

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
