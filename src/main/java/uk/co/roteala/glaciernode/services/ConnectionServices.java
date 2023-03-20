package uk.co.roteala.glaciernode.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.SerializationUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

@Service
@Slf4j
public class ConnectionServices {

    /**
     * Implement sendPayload method and getMessage method
     * Those method implemented in peercreatorfactory
     * */
    public Mono<Void> sendPayload(Connection connection, byte[] payload) {
        log.info("Send payload!:{}", SerializationUtils.deserialize(payload));
        connection.outbound().sendByteArray(Flux.just(payload)).then().subscribe();
        return Mono.empty();
    }

    public Disposable getPayload(Connection connection) {

        Flux<byte[]> v = connection.inbound().receive().asByteArray();
        v.doOnNext(t -> log.info("Data:{}", t));

        return v.subscribe();
    }
}
