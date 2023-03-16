package uk.co.roteala.glaciernode.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.SerializationUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpClientConfig;
import uk.co.roteala.common.TransactionBaseModel;
import uk.co.roteala.glaciernode.storage.StorageCreatorComponent;
import uk.co.roteala.storage.StorageComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Creates each connection for each peer
 * */
@Slf4j
@Configuration
public class PeerCreatorFactory {

    private StorageCreatorComponent storages;

    @Bean
    public List<Connection> connections() {
        List<Connection> connections = new ArrayList<>();

        //For now only connects with server
        Connection connection = TcpClient.create()
                .host("localhost")
                .port(7331)
//                .handle((in, out) -> {
////                    Flux<byte[]> v = in.receive().asByteArray();
////                    v.doOnNext(t -> log.info("Data:{}", SerializationUtils.deserialize(t))).subscribe();
//                    final TransactionBaseModel tx = new TransactionBaseModel();
//
//                    tx.setTo("test");
//                    tx.setTransactionIndex(1);
//                    tx.setBlockHash("asdasdasda");
//
//                    return out.sendByteArray(Flux.just(SerializationUtils.serialize(tx))).neverComplete();
//                })
                .doOnConnect(c -> log.info("Client connected!"))
                .doOnDisconnected(c -> log.info("Client disconnected!"))
                .connectNow();

        connections.add(connection);

        return connections;
    }
}
