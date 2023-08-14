package uk.co.roteala.glaciernode.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.internal.ws.MessageInflater;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpClient;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.*;
import uk.co.roteala.common.monetary.AmountDTO;
import uk.co.roteala.common.monetary.Fund;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.exceptions.MessageSerializationException;
import uk.co.roteala.exceptions.MiningException;
import uk.co.roteala.exceptions.StorageException;
import uk.co.roteala.exceptions.errorcodes.MessageSerializationErrCode;
import uk.co.roteala.exceptions.errorcodes.MiningErrorCode;
import uk.co.roteala.exceptions.errorcodes.StorageErrorCode;
import uk.co.roteala.glaciernode.handlers.ClientTransmissionHandler;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.PeersConnectionFactory;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.net.Peer;
import uk.co.roteala.utils.BlockchainUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private PeersConnectionFactory peersConnectionFactory;

    @Autowired
    private MiningWorker worker;

    @Autowired
    private StorageServices storage;

    @Autowired
    private MoveFund moveFund;

    private NettyOutbound outbound;

    private Connection connection;

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
                processMessageMempool(message);
                break;
            case STATECHAIN:
                processMessageStateChain(message);
                break;
            case ACCOUNT:
                processMessageAccount(message);
                break;
            case PEERS:
                processMessagePeers(message);
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
        }
    }

    private void processTransaction(Message message) {
        MessageActions messageAction = message.getMessageAction();

        Transaction transaction = (Transaction) message.getMessage();

        ChainState state = storage.getStateTrie();

        NodeState nodeState = storage.getNodeState();

        //TODO:Change exception
        switch (messageAction) {
            case APPEND:
                try {
                    if(transaction == null) {
                        throw new StorageException(StorageErrorCode.TRANSACTION_NOT_FOUND);
                    }

                    if(storage.getTransactionByKey(transaction.getHash()) != null) {
                        throw new StorageException(StorageErrorCode.TRANSACTION_NOT_FOUND);
                    }

                    AccountModel sourceAccount = storage.getAccountByAddress(transaction.getFrom());

                    storage.addTransaction(transaction.getHash(), transaction);
                    //log.info("New transaction added:{}", transaction);

                    Fund fund = Fund.builder()
                            .targetAccountAddress(transaction.getTo())
                            .isProcessed(true)
                            .sourceAccount(sourceAccount)
                            .amount(AmountDTO.builder()
                                    .rawAmount(transaction.getValue())
                                    .fees(transaction.getFees())
                                    .build())
                            .build();

                    moveFund.execute(fund);
                } catch (Exception e) {
                    log.error("Error:{}", e.getMessage());
                }
                break;
        }
    }

    private void processBlock(Message message){
        MessageActions messageAction = message.getMessageAction();

        Block block = (Block) message.getMessage();

        ChainState state = storage.getStateTrie();

        NodeState nodeState = storage.getNodeState();

        //TODO:Change exception
        switch (messageAction) {
            case APPEND:
                try {
                    if(block == null) {
                        throw new MiningException(MiningErrorCode.BLOCK_NOT_FOUND);
                    }

                    if(storage.getBlockByIndex(block.getHeader().getIndex()) != null) {
                        throw new StorageException(StorageErrorCode.BLOCK_NOT_FOUND);
                    }

                    state.setLastBlockIndex(state.getLastBlockIndex() + 1);
                    nodeState.setUpdatedAt(System.currentTimeMillis());
                    nodeState.setRemainingBlocks(nodeState.getRemainingBlocks() - 1);

                    storage.addNodeState(nodeState);
                    log.info("Updated node state:{}", nodeState);

                    storage.addStateTrie(state);
                    log.info("Updated chain state:{}", state);

                    storage.addBlock(String.valueOf(block.getHeader().getIndex()), block);
                    log.info("New block added to the chain:{}", block.getHash());

                } catch (Exception e) {
                    log.error("Error:{}", e.getMessage());
                }
                break;
        }
    }

    private void processBlockHeader(Message message) {
        MessageActions messageAction = message.getMessageAction();

        BlockHeader blockHeader = (BlockHeader) message.getMessage();

        final BlockHeaderProcessor blockHeaderProcessor =
                new BlockHeaderProcessor(blockHeader, storage, moveFund, worker);

        switch (messageAction) {
            case APPEND_MINED_BLOCK:
                blockHeaderProcessor.processAppendBlockHeader();
                break;
        }
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

        MessageWrapper messageWrapper = new MessageWrapper();

        ChainState currentState = storage.getStateTrie();
        NodeState nodeState;

        switch (action) {
            //Response to REQUEST
            case APPEND:
                storage.addStateTrie(state);

                worker.setHasStateSync(true);

                nodeState = new NodeState();
                nodeState.setRemainingBlocks(state.getLastBlockIndex());
                nodeState.setUpdatedAt(System.currentTimeMillis());
                nodeState.setLastBlockIndex(0);

                storage.addNodeState(nodeState);

                if(state.getLastBlockIndex() == 0) {
                    worker.setHasDataSync(true);
                }
                //Ask for peers
                messageWrapper.setAction(MessageActions.REQUEST);
                messageWrapper.setType(MessageTypes.PEERS);

                this.outbound.sendObject(Mono.just(messageWrapper.serialize()))
                        .then().subscribe();

                log.info("State:{} chain added!", state);

                break;

                //Response to REQUEST_SYNC
            case MODIFY:
                nodeState = storage.getNodeState();

                log.info("Current chain state:{}", currentState);
                log.info("Current node state:{}", nodeState);

                //Compute the remaining blocks
                final Integer remainingBlocks = currentState.getLastBlockIndex() - nodeState.getLastBlockIndex();

                if(remainingBlocks == 0) {
                    worker.setHasDataSync(true);
                } else {
                    messageWrapper.setType(MessageTypes.NODESTATE);
                    messageWrapper.setAction(MessageActions.REQUEST_SYNC);
                    messageWrapper.setContent(nodeState);

                    this.outbound.sendObject(Mono.just(messageWrapper.serialize()))
                            .then().subscribe();
                }

                nodeState.setRemainingBlocks(remainingBlocks);
                nodeState.setUpdatedAt(System.currentTimeMillis());

                storage.addNodeState(nodeState);


                //Check if peers
                if(storage.getPeersFromStorage().isEmpty()) {
                    messageWrapper.setAction(MessageActions.REQUEST);
                    messageWrapper.setType(MessageTypes.PEERS);

                    this.outbound.sendObject(Mono.just(messageWrapper.serialize()))
                            .then().subscribe();
                } else {
                    this.peersConnectionFactory.createConnections(message);
                }

                storage.addStateTrie(state);
                worker.setHasStateSync(true);

                log.info("State:{} and node chain:{} modified!", state, nodeState);
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

                PseudoTransaction existingMempool = storage.getMempoolTransactionByKey(mempoolTransaction.getPseudoHash());

                if(mempoolTransaction.verifySignatureWithRecovery() && existingMempool == null){
                    AccountModel sourceAccount = storage.getAccountByAddress(mempoolTransaction.getFrom());

                    Fund fund = Fund.builder()
                            .isProcessed(false)
                            .sourceAccount(sourceAccount)
                            .targetAccountAddress(mempoolTransaction.getTo())
                            .amount(AmountDTO.builder()
                                    .fees(mempoolTransaction.getFees())
                                    .rawAmount(mempoolTransaction.getValue())
                                    .build())
                            .build();

                    moveFund.execute(fund);

                    storage.addMempool(mempoolTransaction.getPseudoHash(), mempoolTransaction);
                    worker.setHasMempoolData(true);
                }
                break;
        }
    }

    private void processMessagePeers(Message message){
        MessageActions action = message.getMessageAction();

        PeersContainer container = (PeersContainer) message.getMessage();

        NodeState nodeState = storage.getNodeState();

        switch (action) {
            /**
             * Response to REQUEST
             * Create connections from list of IPs
             * */
            case NO_CONNECTIONS:
                MessageWrapper messageWrapper = new MessageWrapper();
                messageWrapper.setType(MessageTypes.NODESTATE);
                messageWrapper.setAction(MessageActions.REQUEST_SYNC);
                messageWrapper.setContent(nodeState);

                this.outbound.sendObject(Mono.just(messageWrapper.serialize()))
                        .then().subscribe();
                break;
        }
    }


    private void processVerifyBlockRequest(BlockHeader blockHeader, ChainState state) {
        try {

        } catch (Exception e) {
            log.error("Error while processing verify request:{}", e.getMessage());
        }
    }



    //TODO:Add try and catch

}
