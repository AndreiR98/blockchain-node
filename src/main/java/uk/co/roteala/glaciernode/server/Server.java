package uk.co.roteala.glaciernode.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpServer;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class Server {

    private boolean canCreate = false;

    //@Bean
    public void initServer() {
        TcpServer
                .create()
                .doOnConnection(connection -> log.info("Peer connected from:{}"))
                .host("0.0.0.0")
                .port(7331)
                .doOnBound(c -> {
                    log.info("Server started!");
                })
                .doOnUnbound(c -> {
                    log.info("Server stoped!");
                })
                .bindNow();
    }

    public boolean getStatus() {
        return this.canCreate;
    }

    /**
     * When client connects to server
     * */
    //public Consumer<Connection> doOnConnectionHandler(){
        //return null;
    //}
}
