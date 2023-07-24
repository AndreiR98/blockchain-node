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
public class ServerConnectionStorage implements Consumer<Connection> {
    @Getter
    private List<Connection> serverConnections;

    @Override
    public void accept(Connection connection) {
        this.serverConnections.add(connection);
    }
}
