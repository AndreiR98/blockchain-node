package uk.co.roteala.glaciernode.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.PseudoTransaction;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.glaciernode.processor.MessageProcessor;
import uk.co.roteala.glaciernode.processor.Processor;
import uk.co.roteala.glaciernode.storage.StorageServices;
import org.apache.commons.lang3.SerializationUtils;

import java.time.Duration;
import java.util.function.BiFunction;

@Slf4j
@RequiredArgsConstructor
public class BrokerConnectionHandler implements BiFunction<NettyInbound, NettyOutbound, Flux<Void>> {
    @Autowired
    private MessageProcessor messageProcessor;
    @Override
    public Flux<Void> apply(NettyInbound inbound, NettyOutbound outbound) {

        messageProcessor.process(inbound, outbound);

        return Flux.never();
    }
}
