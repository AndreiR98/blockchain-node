package uk.co.roteala.glaciernode.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpServer;

import java.util.function.Consumer;

@Slf4j
@Component
public class Server {

//    public void initServer() {
//        TcpServer
//                .create()
//                .doOnConnection(doOnConnectionHandler())
//                //.doOnChannelInit()
//                //.handle((in, out) -> {})
//                .port(7331)
//                .doOnBound()
//                .doOnUnbound()
//                .bindNow()
//                .onDispose()
//                .block();
//    }

//    public Consumer<Connection> doOnConnectionHandler(){
//
//    }
}
