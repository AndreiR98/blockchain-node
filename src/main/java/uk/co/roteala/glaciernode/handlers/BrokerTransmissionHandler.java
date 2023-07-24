package uk.co.roteala.glaciernode.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.glaciernode.processor.BrokerMessageProcessor;

import java.util.concurrent.Flow;
import java.util.function.BiFunction;

/**
 * Handles the broker transmission incoming of messages are forwarded to the processor
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerTransmissionHandler implements BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> {

    private final BrokerMessageProcessor brokerMessageProcessor;

    @Override
    public Publisher<Void> apply(NettyInbound inbound, NettyOutbound outbound) {

        brokerMessageProcessor.forwardMessage(inbound, outbound);

        return Flux.never();
    }
}
