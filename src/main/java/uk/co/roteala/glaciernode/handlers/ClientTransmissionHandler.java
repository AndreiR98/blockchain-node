//package uk.co.roteala.glaciernode.handlers;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.reactivestreams.Publisher;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Flux;
//import reactor.netty.NettyInbound;
//import reactor.netty.NettyOutbound;
//
//import java.util.function.BiFunction;
//
///**
// * Client transmission handler receiving messages from other server and forwards them to the message processor
// * */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class ClientTransmissionHandler implements BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> {
//
//    private final ClientMessageProcessor clientMessageProcessor;
//
//    @Override
//    public Publisher<Void> apply(NettyInbound inbound, NettyOutbound outbound) {
//
//        clientMessageProcessor.forwardMessage(inbound, outbound);
//
//        return outbound.neverComplete();
//    }
//}
