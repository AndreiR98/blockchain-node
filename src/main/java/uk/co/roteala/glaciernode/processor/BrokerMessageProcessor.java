package uk.co.roteala.glaciernode.processor;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import okhttp3.internal.ws.MessageInflater;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.PseudoTransaction;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
import uk.co.roteala.glaciernode.storage.StorageServices;

/**
 * Process messages coming from broker
 * Can send to:
 * Other servers, other clients
 * */
@Slf4j
@Component
public class BrokerMessageProcessor implements Processor {
    @Autowired
    private ServerConnectionStorage serverConnectionStorage;

    @Autowired
    private ClientConnectionStorage clientConnectionStorage;

    @Autowired
    private MiningWorker worker;

    @Autowired
    private StorageServices storage;

    public void forwardMessage(NettyInbound inbound, NettyOutbound outbound){
        inbound.receive().retain()
                .map(this::mapToMessage)
                .doOnNext(message -> {
                    inbound.withConnection(message::setConnection);

                    this.process(message);
                })
                .then()
                .subscribe();
    }

    @Override
    public void process(Message message) {
        MessageTypes messageTypes = message.getMessageType();


        switch (messageTypes) {
            case MEMPOOL:
                processMessageMempool(message);
                break;
            case STATECHAIN:
                processMessageStateChain(message);
                break;
            case ACCOUNT:
                processMessageAccount(message);
                break;
        }
        log.info("Message {} is going to be processed", message);
    }

    private void processMessageAccount(Message message) {
        MessageActions action = message.getMessageAction();

        switch (action) {
            case APPEND:
                storage.updateAccount((AccountModel) message.getMessage());
                break;
        }
    }

    private void processMessageStateChain(Message message) {
        MessageActions action = message.getMessageAction();
        ChainState state = (ChainState) message.getMessage();

        switch (action) {
            case APPEND:
                storage.addStateTrie(state);
                storage.addBlock(String.valueOf(state.getGetGenesisBlock().getIndex()), state.getGetGenesisBlock());

                worker.setCanMine(true);
                break;
        }
    }

    private void processMessageMempool(Message message) {
        //MessageActions action = message.getMessageAction();
        MessageActions action = MessageActions.APPEND;

        switch (action) {
            /**
             * Before adding it to the mempool storage check the signature
             * */
            case APPEND:
                PseudoTransaction mempoolTransaction = (PseudoTransaction) message.getMessage();

                if(mempoolTransaction.verifySignatureWithRecovery()){
                    log.info("Adding new mempool!");
                    storage.addMempool(mempoolTransaction.getPseudoHash(), mempoolTransaction);
                    worker.setCanMine(true);
                }
                break;
        }
    }
}
