package uk.co.roteala.glaciernode.client;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import uk.co.roteala.common.BaseEmptyModel;
import uk.co.roteala.common.events.Event;
import uk.co.roteala.common.events.EventsMessageFactory;
import uk.co.roteala.glaciernode.services.ConnectionServices;
import uk.co.roteala.glaciernode.storage.StorageCreatorComponent;
import uk.co.roteala.net.Peer;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Creates each connection for each peer
 * */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PeerCreatorFactory {

    @Autowired
    private StorageCreatorComponent storages;

    private List<Connection> connection;

    //@Bean
    public List< Connection> peersConnection() throws RocksDBException {
//        RocksIterator peerIterator = this.storages.peers().getRaw().newIterator();
//
//        List<Peer> peers = new ArrayList<>();
//
//        for(peerIterator.seekToFirst(); peerIterator.isValid(); peerIterator.next()){
//            peers.add((Peer) SerializationUtils.deserialize(peerIterator.value()));
//        }


        TcpClient.create()
                .host("18.133.139.246")
                .option(ChannelOption.SO_KEEPALIVE, true)
                .port(7331)
                .handle(((inbound, outbound) -> {

                    Flux.just(
                                    EventsMessageFactory.peers().serialize(),
                                    EventsMessageFactory.genesisTX().serialize(),
                                    EventsMessageFactory.genesisBL().serialize())
                            .map(bytes -> {
                                Event event = (Event) SerializationUtils.deserialize(bytes);
                                log.info("Send:{}", event);
                                return event;
                            })
                            .flatMap(event -> {
                                return outbound.sendByteArray(Flux.just(SerializationUtils.serialize(event)));
                            }).subscribe();


                    //Handle incoming data
                    inbound.receive().asByteArray()
                            .flatMap(bytes -> {
                                BaseEmptyModel model = (BaseEmptyModel) SerializationUtils.deserialize(bytes);

                                //handleData(model);

                                log.info("Received:{}", model);
                                return Flux.never();
                            })
                            .subscribe();
                    return outbound.neverComplete();
                }))
                .doOnConnect(c -> log.info("Seeder connected!"))
                .doOnDisconnected(c -> log.info("Seeder disconnected!"))
                .connectNow();
        return null;
    }

    public List<Connection> getP2P() {
        return this.connection;
    }
}
