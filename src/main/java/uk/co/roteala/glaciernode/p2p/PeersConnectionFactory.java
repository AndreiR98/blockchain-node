package uk.co.roteala.glaciernode.p2p;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.netty.Connection;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.PeerMessage;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.net.Peer;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PeersConnectionFactory {
    private static StorageServices storage;

    private static ClientConnectionStorage clientConnectionStorage;

    private static BrokerConnectionStorage brokerConnectionStorage;

    public PeersConnectionFactory(StorageServices storage, ClientConnectionStorage clientConnectionStorage,
                                  BrokerConnectionStorage brokerConnectionStorage) {
        this.storage = storage;
        this.clientConnectionStorage = clientConnectionStorage;
        this.brokerConnectionStorage = brokerConnectionStorage;
    }

    public static PeersConnectionFactory storages(StorageServices storage, ClientConnectionStorage clientConnectionStorage,BrokerConnectionStorage brokerConnectionStorage) {
        return new PeersConnectionFactory(storage, clientConnectionStorage, brokerConnectionStorage);
    }

    /**
     * Try to create connections with existing peers if not enough ask peers
     * If storage empty ask broker for peers
     * */
    public static void create() {
        List<Peer> storedPeers = storage.getPeersFromStorage();

        if(storedPeers.isEmpty()) {
            PeerMessage peerMessage = new PeerMessage(null);
            peerMessage.setMessageAction(MessageActions.REQUEST_SYNC);
        } else {
            Flux.fromIterable(storedPeers)
                    .doOnNext(peer -> {
                        log.info("Creating peers connection with:{}", peer.getAddress());
                         clientConnectionStorage.getClientConnections();
                    })
                    .doFinally(signalType -> {
                        if(signalType == SignalType.ON_COMPLETE) {
                            //Count how many connections
                            if(!clientConnectionStorage.getClientConnections().isEmpty()) {
                                //Request more and process it in the message processor
                            } else {

                            }
                        }
                    })
        }

//        if(storedPeers.isEmpty()){
//            PeerMessage peerMessage = new PeerMessage();
//            peerMessage.setMessageAction(MessageActions.REQUEST_SYNC);
//        }

        Flux.fromIterable(storedPeers)
                .doOnNext(peer -> {
                    log.info("Creating peers connections with");
                    clientConnectionStorage.getClientConnections();
                });
    }
}
