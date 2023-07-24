package uk.co.roteala.glaciernode.processor;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;

/**
 * Process messages coming from broker
 * Can send to:
 * Other servers, other clients
 * */
@Slf4j
@Component
public class BrokerMessageProcessor implements Processor {
    @Autowired
    private ServerConnectionStorage serverConnectionStorage;

    @Autowired
    private ClientConnectionStorage clientConnectionStorage;

    public void forwardMessage(NettyInbound inbound, NettyOutbound outbound){
        inbound.receive().retain()
                .map(this::mapToMessage)
                .doOnNext(message -> {
                    inbound.withConnection(message::setConnection);

                    this.process(message);
                })
                .then()
                .subscribe();
    }

    @Override
    public void process(Message message) {

    }
}
