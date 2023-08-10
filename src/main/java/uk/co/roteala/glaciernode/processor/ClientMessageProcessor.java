package uk.co.roteala.glaciernode.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.BrokerConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
import uk.co.roteala.glaciernode.storage.StorageServices;

/**
 * Process messages coming from other servers
 * Can broadcast to:
 * Broker, Other clients connected to this node server
 * */
@Slf4j
@Component
public class ClientMessageProcessor implements Processor {

    @Autowired
    private StorageServices storage;

    public void forwardMessage(NettyInbound inbound, NettyOutbound outbound){
        inbound.receive().retain()
                .map(this::mapper)
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
