package uk.co.roteala.glaciernode.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

@Service
@Slf4j
public class PeerServices {

    /**
     * Implement sendPayload method and getMessage method
     * Those method implemented in peercreatorfactory
     * */
    public Mono<Void> sendPayload(Connection connection, byte[] payload) {
        log.info("Send payload!");
        connection.outbound().sendByteArray(Flux.just(payload)).neverComplete().subscribe();
        return Mono.empty();
    }

    public Flux<byte[]> getPayload(Connection connection) {

        Flux<byte[]> v = connection.inbound().receive().asByteArray();
        v.doOnNext(t -> log.info("Data:{}", SerializationUtils.deserialize(t))).subscribe();

        return v;
    }
}
