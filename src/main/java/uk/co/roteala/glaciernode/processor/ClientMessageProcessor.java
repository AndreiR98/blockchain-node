package uk.co.roteala.glaciernode.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.monetary.AmountDTO;
import uk.co.roteala.common.monetary.Fund;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.BrokerConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
import uk.co.roteala.glaciernode.processor.client.ClientBlockHeaderProcessor;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.time.Duration;
import java.util.List;

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

    @Autowired
    private MoveFund moveFund;

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
                processTransaction(message);
                break;
            case BLOCK:
                processBlock(message);
                break;
            case NODESTATE:
                processNodeState(message); //Only to sync the node
                break;
        }
    }

    private void processNodeState(Message message) {
        MessageActions messageActions = message.getMessageAction();

        NodeState nodeState = (NodeState) message.getMessage();

        final NodeStateProcessor nodeStateProcessor =
                new NodeStateProcessor(storage, nodeState, connection);

        switch (messageActions) {
            case REQUEST_SYNC:
                nodeStateProcessor.processRequestSync();
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
        }
    }

    /**
     * Appedn transaction to the chain
     * */
    private void processTransaction(Message message) {
        try {
            Transaction transaction = (Transaction) message.getMessage();

            AccountModel sourceAccount = this.storage.getAccountByAddress(transaction.getFrom());

            Fund fund = Fund.builder()
                    .isProcessed(true)
                    .amount(new AmountDTO(transaction.getValue(), transaction.getFees()))
                    .sourceAccount(sourceAccount)
                    .targetAccountAddress(transaction.getTo())
                    .build();

            moveFund.reverseFunding(fund);

            this.storage.addTransaction(transaction.getHash(), transaction);
            log.info("Transaction:{} added to the chain!",transaction.getHash());

        } catch (Exception e) {
            log.error("Failing synchronizing transaction:{}",e.getMessage());
        }
    }

    /**
     * Append block to the chain
     * */
    private void processBlock(Message message) {
        try {
            Block block = (Block) message.getMessage();

            List<String> transactionList = this.storage.groupSyncTransaction(block.getHeader().getIndex());

            block.setTransactions(transactionList);

            this.storage.addBlock(String.valueOf(block.getHeader().getIndex()), block);
            log.info("Block of index:{} synchronized", block.getHeader().getIndex());
        } catch (Exception e) {
            log.error("Failing synchronizing block:{}", e.getMessage());
        }
    }
}
