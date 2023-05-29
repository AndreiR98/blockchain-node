package uk.co.roteala.glaciernode.server;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.tcp.TcpServer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Server {

    @Bean
    public void setUpServer() {
        TcpServer.create()
                .port(7331)
                .doOnConnection(c -> log.info("Connection received from:{}", c.address()))
                .doOnBound(s -> log.info("Server started on:{}!", s.port()))
                .option(ChannelOption.SO_KEEPALIVE, true)
                .bindNow()
                .onDispose()
                .block();
    }
}
