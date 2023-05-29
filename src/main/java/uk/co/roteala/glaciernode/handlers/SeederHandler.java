package uk.co.roteala.glaciernode.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Mono;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.glaciernode.storage.StorageInterface;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.net.Peer;

import java.util.List;
import java.util.function.BiFunction;

@Slf4j
@RequiredArgsConstructor
public class SeederHandler implements BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> {
    private final StorageServices storageServices;

    @Override
    public Publisher<Void> apply(NettyInbound inbound, NettyOutbound outbound) {
        inbound.receive()
                .asByteArray()
                .map(bytes -> {
                    log.info("Serialized peer:{}", SerializationUtils.deserialize(bytes));
                    return (List<Peer>) SerializationUtils.deserialize(bytes);
                })
                .doOnNext(peers -> {
                    log.info("Storage:{}", storageServices);
                    log.info("List of peers:{}", peers);
                    peers.forEach(storageServices::addPeer);
                })
                .then().subscribe();

        return outbound.neverComplete();
    }
}
