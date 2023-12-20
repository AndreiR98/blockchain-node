package uk.co.roteala.glaciernode.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.messenger.MessengerUtils;
import uk.co.roteala.glaciernode.p2p.AssemblerMessenger;
import uk.co.roteala.glaciernode.p2p.ExecutorMessenger;

import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.function.BiFunction;

/**
 * Handles the broker transmission incoming of messages are forwarded to the processor
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerTransmissionHandler implements BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> {

    @Autowired
    private AssemblerMessenger assembler;

    @Autowired
    private ExecutorMessenger executor;

    @Override
    public Mono<Void> apply(NettyInbound inbound, NettyOutbound outbound) {
        inbound.receive().retain()
                .parallel(4)
                .map(MessengerUtils::deserialize) // Deserialize into message
                .map(this.assembler) // Assemble chunks
                .flatMap(optionalTemplate -> optionalTemplate.map(Mono::just).orElseGet(Mono::empty))
                //.flatMap(Optional::stream)
                .doOnNext(template -> {
                    inbound.withConnection(template::setOwner);
                })
                .doOnNext(this.executor)
                .then()
                .subscribe();

        return outbound.neverComplete();
    }
}
