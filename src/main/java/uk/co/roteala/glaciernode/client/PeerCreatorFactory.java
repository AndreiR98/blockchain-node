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
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import uk.co.roteala.common.BaseEmptyModel;
import uk.co.roteala.common.events.EventsMessageFactory;
import uk.co.roteala.glaciernode.services.ConnectionServices;
import uk.co.roteala.glaciernode.storage.StorageCreatorComponent;
import uk.co.roteala.net.Peer;

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
    public void peersConnection() throws RocksDBException {
        RocksIterator peerIterator = this.storages.peers().getRaw().newIterator();

        List<Peer> peers = new ArrayList<>();

        for(peerIterator.seekToFirst(); peerIterator.isValid(); peerIterator.next()){
            peers.add((Peer) SerializationUtils.deserialize(peerIterator.value()));
        }


        peers.forEach(peer -> {
            Connection connection = TcpClient.create()
                    .host(peer.getAddress())
                    .port(7331)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handle(((inbound, outbound) -> {
                        return outbound.neverComplete();
                    }))
                    .doOnConnect(c -> log.info("P2P connection established!"))
                    .doOnDisconnected(c -> log.info("Disconnected!"))
                    .connectNow();

            this.connection.add(connection);
        });
    }

    public List<Connection> getP2P() {
        return this.connection;
    }
}
