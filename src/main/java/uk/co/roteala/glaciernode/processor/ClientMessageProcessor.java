package uk.co.roteala.glaciernode.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.BlockHeader;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.BrokerConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.time.Duration;

/**
 * Process messages coming from other servers
 * Can broadcast to:
 * Broker, Other clients connected to this node server
 * */
@Slf4j
@Component
public class ClientMessageProcessor implements Processor {

    @Autowired
    private StorageServices storage;

    @Autowired
    private ClientConnectionStorage clientConnectionStorage;

    @Autowired
    private ServerConnectionStorage serverConnectionStorage;

    @Autowired
    private BrokerConnectionStorage brokerConnectionStorage;

    @Autowired
    private MiningWorker miningWorker;

    private Connection connection;

    private NettyOutbound outbound;

    public void forwardMessage(NettyInbound inbound, NettyOutbound outbound){
        this.outbound = outbound;

        inbound.receive().retain()
                .delayElements(Duration.ofMillis(150))
                .parallel()
                .map(this::mapper)
                .doOnNext(message -> {
                    log.info("Received:{}", message);
                    inbound.withConnection(connection ->{
                        message.setConnection(connection);
                        this.connection = connection;
                    });
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
                //processMessageMempool(message);
                break;
            case STATECHAIN:
                //processMessageStateChain(message);
                break;
            case PEERS:
                //processMessagePeers(message);
                break;
            case BLOCKHEADER:
                processBlockHeader(message);
                break;
            case TRANSACTION:
                //processTransaction(message);
                break;
            case BLOCK:
                //processBlock(message);
                break;
        }
    }

    private void processBlockHeader(Message message) {
        MessageActions messageAction = message.getMessageAction();

        BlockHeader blockHeader = (BlockHeader) message.getMessage();

        final ClientBlockHeaderProcessor blockHeaderProcessor =
                new ClientBlockHeaderProcessor(blockHeader, connection, storage, clientConnectionStorage, serverConnectionStorage,
                        brokerConnectionStorage, miningWorker);

        switch (messageAction) {
            case VERIFY:
                blockHeaderProcessor.verifyNewBlock();
                break;
            case REQUEST_SYNC:
                //blockHeaderProcessor.syncWithNewHeader();
                break;
        }
    }
}
