package uk.co.roteala.glaciernode.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpClient;
import uk.co.roteala.common.BaseBlockModel;
import uk.co.roteala.common.BaseEmptyModel;
import uk.co.roteala.common.TransactionBaseModel;
import uk.co.roteala.common.events.Event;
import uk.co.roteala.common.events.EventsMessageFactory;
import uk.co.roteala.glaciernode.services.ConnectionServices;
import uk.co.roteala.glaciernode.storage.StorageCreatorComponent;
import uk.co.roteala.net.Peer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * Seeder class that handles all the connections between peers
 * */
@Component
@Slf4j
@EnableScheduling
@RequiredArgsConstructor
public class Seeder {
    @Autowired
    private StorageCreatorComponent storages;
    private Connection connection;

    @Bean
    public void seederConnection() {
        this.connection = TcpClient.create()
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

                                handleData(model);

                                log.info("Received:{}", model);
                                return Flux.never();
                            })
                            .subscribe();
                    return outbound.neverComplete();
                }))
                .doOnConnect(c -> log.info("Seeder connected!"))
                .doOnDisconnected(c -> log.info("Seeder disconnected!"))
                .connectNow();



    }

    //TODO:Add handlers for each type
    private void handleData(BaseEmptyModel data) {

                    if(data instanceof Peer){
                        Peer p = (Peer) data;

                        final byte[] peerKey = p.getAddress().getBytes();

                        try {
                            if(this.storages.peers().get(peerKey) == null){
                                this.storages.peers().add(peerKey, p);
                            } else {
                                //If peer exists
                                Peer peerInStorage = (Peer) this.storages.peers().get(peerKey);
                                peerInStorage.setActive(true);
                                peerInStorage.setLastTimeSeen(System.currentTimeMillis());

                                this.storages.peers().add(peerKey, peerInStorage);
                            }
                        } catch (RocksDBException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    if(data instanceof TransactionBaseModel){
                        //do transaction
                        TransactionBaseModel tx = (TransactionBaseModel) data;
                        final byte[] txKey = tx.getHash().getBytes();

                        try {
                            if(this.storages.tx().get(txKey) == null){
                                this.storages.tx().add(txKey, tx);
                            }
                        } catch (RocksDBException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    if(data instanceof BaseBlockModel){
                        //do block
                        BaseBlockModel block = (BaseBlockModel) data;
                        final byte[] blKey = block.getIndex().toString().getBytes();

                        try {
                            if(this.storages.blocks().get(blKey) == null){
                                this.storages.blocks().add(blKey, block);
                            }
                        } catch (RocksDBException e) {
                            throw new RuntimeException(e);
                        }
                    }

    }
}
