package uk.co.roteala.glaciernode.p2p;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import uk.co.roteala.glaciernode.handlers.ClientTransmissionHandler;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.net.Peer;

import java.net.SocketAddress;
import java.util.function.Supplier;

@Slf4j
@Service
public class PeersConnectionFactory {

    @Autowired
    private StorageServices storage;
    @Autowired
    private Supplier<SocketAddress> addressSupplier;

    @Autowired
    private ClientTransmissionHandler clientTransmissionHandler;

    @Autowired
    private ClientConnectionStorage clientConnectionStorage;

    /**
     * Initialize connections with peers or ask broker for that
     *
     * @return
     */

    public Mono<? extends Connection> createConnection(Peer peer) {
        return TcpClient.create()
                .host(peer.getAddress())
                .port(7331)
                .bindAddress(addressSupplier)
                .doOnConnected(c -> log.info("Peer connected!"))
                .handle(clientTransmissionHandler)
                .doOnDisconnected(connection -> {
                    clientConnectionStorage.getClientConnections().remove(connection);

                    peer.setActive(false);
                    peer.setLastTimeSeen(System.currentTimeMillis());
                    this.storage.addPeer(peer);
                })
                .connect()
                .doOnSuccess(connection -> {
                    clientConnectionStorage.getClientConnections().add(connection);
                    peer.setActive(true);
                    //peer.setLastTimeSeen(System.currentTimeMillis());
                    this.storage.addPeer(peer);
                })
                .doOnError(throwable -> {
                    log.error("Failed to connect....", throwable.getMessage());
                })
                .onErrorResume(throwable -> Mono.empty());
    }
}
