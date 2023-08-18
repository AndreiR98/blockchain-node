package uk.co.roteala.glaciernode.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.glaciernode.processor.ClientMessageProcessor;
import uk.co.roteala.glaciernode.processor.ServerMessageProcessor;

import java.util.function.BiFunction;

/**
 * Server transmission handler, receiving messages from other clients, forwards them to the processor;
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerTransmissionHandler implements BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> {
    private final ServerMessageProcessor serverMessageProcessor;

    @Override
    public Publisher<Void> apply(NettyInbound inbound, NettyOutbound outbound) {

        serverMessageProcessor.forwardMessage(inbound, outbound);

        return outbound.neverComplete();
    }
}
