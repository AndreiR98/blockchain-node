package uk.co.roteala.glaciernode.processor;

import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.events.Message;

public interface Processor {
    void process(NettyInbound inbound, NettyOutbound outbound);
}
