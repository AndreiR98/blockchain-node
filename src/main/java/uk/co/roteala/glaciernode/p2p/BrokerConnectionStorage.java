package uk.co.roteala.glaciernode.p2p;

import io.netty.buffer.Unpooled;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.events.ChainStateMessage;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.events.MessageWrapper;
import uk.co.roteala.glaciernode.miner.Miner;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerConnectionStorage implements Consumer<Connection> {
    @Getter
    private Connection connection;

    private final StorageServices storage;

    @Autowired
    private MiningWorker miningWorker;

    //private final PeersConnectionFactory peersConnectionFactory;

    @Override
    public void accept(Connection connection) {
        log.info("Connection with broker established!");
        this.connection = connection;

        ChainState state = storage.getStateTrie();

        MessageWrapper messageWrapper = new MessageWrapper();
        //Check state trie and sync
        if(state == null) {
            messageWrapper.setAction(MessageActions.REQUEST);
            messageWrapper.setType(MessageTypes.STATECHAIN);
        } else {
            messageWrapper.setAction(MessageActions.REQUEST_SYNC);
            messageWrapper.setType(MessageTypes.STATECHAIN);
        }

        connection.outbound().sendObject(Mono.just(messageWrapper.serialize()))
                .then().subscribe();

        miningWorker.setBrokerConnected(true);
    }
}
