package uk.co.roteala.glaciernode.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpServer;

import java.util.function.Consumer;

@Slf4j
@Component
public class Server {

    private boolean canCreate = false;

    @Bean
    public void initServer() {
        TcpServer
                .create()
                //.doOnConnection(doOnConnectionHandler())
                .port(7331)
                .doOnBound(c -> {
                    log.info("Server started!");
                    this.canCreate = true;
                })
                .doOnUnbound(c -> {
                    log.info("Server stoped!");
                    this.canCreate = false;
                })
                .bindNow()
                .onDispose()
                .block();
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
