package uk.co.roteala.glaciernode.client;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.tcp.TcpClient;
import uk.co.roteala.glaciernode.storage.GlacierStorage;
import uk.co.roteala.net.Peer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.function.Consumer;


@Component
@Slf4j
public class Client {
    @Autowired
    private GlacierStorage storage;

    @Bean
    public void startClient() throws IOException, ClassNotFoundException, RocksDBException {
        TcpClient.create()
                .host("18.132.247.83")
                .port(7331)
                .doOnConnected(connectionHandler())
                .connectNow();
        log.info("Client started!");
    }

    public Consumer<Connection> connectionHandler() {
        return connection -> {
            Flux<byte[]> v = connection.inbound().receive().asByteArray();
            v.doOnNext(t -> log.info("Data:{}", SerializationUtils.deserialize(t))).subscribe();
        };
    }

    private Publisher<Void> receiverHandler(NettyInbound inbound){
       inbound
               .receive()
               .asByteArray()
               .flatMap(bytes -> Flux.fromIterable(deserializePeers(bytes)))
               .doOnNext(peer -> log.info("Data:{}", peer))
               .then()
               .subscribe();

       return Mono.empty();
    }

    private Set<Peer> deserializePeers(byte[] encoded) {
        return (Set<Peer>) SerializationUtils.deserialize(encoded);
    }
}
