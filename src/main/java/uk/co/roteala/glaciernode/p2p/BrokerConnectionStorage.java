package uk.co.roteala.glaciernode.p2p;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.netty.Connection;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerConnectionStorage implements Consumer<Connection> {
    @Getter
    private Connection connection;

    @Override
    public void accept(Connection connection) {
        this.connection = connection;
    }
}
