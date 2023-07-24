package uk.co.roteala.glaciernode.p2p;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.netty.Connection;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientConnectionStorage implements Consumer<Connection> {
    @Getter
    private List<Connection> clientConnections;

    @Override
    public void accept(Connection connection) {
        this.clientConnections.add(connection);
    }
}
