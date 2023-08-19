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
        log.info("Trying to connect with:{}", peer.getAddress());

        return TcpClient.create()
                .host(peer.getAddress())
                .port(7331)
                .bindAddress(addressSupplier)
                .doOnConnected(clientConnectionStorage)
                .handle(clientTransmissionHandler)
                .doOnDisconnected(connection -> {
                    log.info("Peer disconnected!");
                    // Remove the connection from the storage
                    clientConnectionStorage.accept(connection);
                    clientConnectionStorage.getClientConnections().remove(connection);
                    peer.setActive(false);
                    peer.setLastTimeSeen(System.currentTimeMillis());
                    this.storage.addPeer(peer);
                })
                .connect()
                .doOnSuccess(connection -> {
                    log.info("Peer connection succeeded!");
                })
                .doOnError(throwable -> {
                    log.error("Failed to connect: " + throwable.getMessage());
                })
                .onErrorResume(throwable -> {
                    log.error("Connection error: " + throwable.getMessage());
                    return Mono.empty();
                });
    }
}
