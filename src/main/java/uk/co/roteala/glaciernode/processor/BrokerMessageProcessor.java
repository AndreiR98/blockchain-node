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
import uk.co.roteala.exceptions.errorcodes.MessageSerializationErrCode;
import uk.co.roteala.exceptions.errorcodes.MiningErrorCode;
import uk.co.roteala.glaciernode.handlers.ClientTransmissionHandler;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.PeersConnectionFactory;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.net.Peer;
import uk.co.roteala.utils.BlockchainUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    private static Message mapperToMessage(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        String messageWrapperString = SerializationUtils.deserialize(bytes);
        ReferenceCountUtil.release(byteBuf);

        MessageTemplate.MessageTemplateBuilder templateBuilder = MessageTemplate.builder();

        try {

            ObjectMapper objectMapper = new ObjectMapper();
            MessageWrapper messageWrapper = objectMapper.readValue(messageWrapperString, MessageWrapper.class);

            templateBuilder
                    .verified(messageWrapper.isVerified())
                    .messageAction(messageWrapper.getAction());

            switch (messageWrapper.getType()) {
                case PEERS:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.PEERS);
                    break;
                case BLOCK:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.BLOCK);
                    break;
                case BLOCKHEADER:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.BLOCKHEADER);
                    break;
                case ACCOUNT:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.ACCOUNT);
                    break;
                case MEMPOOL:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.MEMPOOL);
                    break;
                case STATECHAIN:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.STATECHAIN);
                    break;
                case TRANSACTION:
                    templateBuilder
                            .content(messageWrapper.getContent())
                            .type(MessageTypes.TRANSACTION);
                    break;
            }

            return templateBuilder.build();

        } catch (Exception e) {
            log.info("Exception:{}", e.getMessage());
            throw new MessageSerializationException(MessageSerializationErrCode.DESERIALIZATION_FAILED);
        }
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
        }
    }

    private void processBlockHeader(Message message) {
        MessageActions messageAction = message.getMessageAction();

        BlockHeader blockHeader = (BlockHeader) message.getMessage();

        switch (messageAction) {
            case APPEND_MINED_BLOCK:
                if(blockHeader == null) {
                    throw new MiningException(MiningErrorCode.BLOCK_NOT_FOUND);
                }

                if(storage.getBlockByIndex(blockHeader.getIndex()) != null) {
                    throw new MiningException(MiningErrorCode.ALREADY_EXISTS);
                }

                if(storage.getPseudoBlockByHash(blockHeader.getHash()) == null) {
                    throw new MiningException(MiningErrorCode.BLOCK_NOT_FOUND);
                }

                Block block = storage.getPseudoBlockByHash(blockHeader.getHash());

                List<String> transactionHashes = new ArrayList<>();

                int index = 0;
                for(String pseudoHash : block.getTransactions()) {
                    //Create a transaction
                    PseudoTransaction pseudoTransaction = storage.getMempoolTransactionByKey(pseudoHash);

                    if(pseudoTransaction == null) {
                        throw new MiningException(MiningErrorCode.PSEUDO_TRANSACTION_NOT_FOUND);
                    }

                    Transaction transaction = BlockchainUtils
                            .mapPsuedoTransactionToTransaction(pseudoTransaction, blockHeader, index);


                    AccountModel sourceAccount = storage.getAccountByAddress(transaction.getFrom());

                    AmountDTO amountDTO = new AmountDTO(transaction.getValue(), transaction.getFees());

                    Fund fund = Fund.builder()
                            .isProcessed(true)
                            .sourceAccount(sourceAccount)
                            .amount(amountDTO)
                            .targetAccountAddress(transaction.getTo())
                            .build();

                    moveFund.execute(fund);

                    storage.addTransaction(transaction.getHash(), transaction);
                    storage.deleteMempoolTransaction(transaction.getPseudoHash());

                    transactionHashes.add(transaction.getHash());

                    index++;
                }

                block.setTransactions(transactionHashes);
                storage.addBlock(String.valueOf(block.getHeader().getIndex()), block);
                storage.deleteMempoolBlocksAtIndex(block.getHeader().getIndex());

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

        switch (action) {
            //Response to REQUEST
            case APPEND:
                storage.addStateTrie(state);

                worker.setHasStateSync(true);

                //Ask for peers
                messageWrapper.setAction(MessageActions.REQUEST);
                messageWrapper.setType(MessageTypes.PEERS);

                outbound.sendObject(Mono.just(messageWrapper.serialize()))
                        .then().subscribe();

                log.info("State chain added!");

                break;

                //Response to REQUEST_SYNC
            case MODIFY:
                storage.addStateTrie(state);

                //Compute the remaining blocks
                final Integer remainingBlocks = state.getLastBlockIndex() - currentState.getLastBlockIndex();

                if(remainingBlocks == 0) {
                    worker.setHasDataSync(true);
                }

                NodeState nodeState = new NodeState();
                nodeState.setRemainingBlocks(remainingBlocks);
                nodeState.setUpdatedAt(Instant.now());

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

                worker.setHasStateSync(true);

                log.info("State and node chain modified!");
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

        switch (action) {
            /**
             * Response to REQUEST
             * Create connections from list of IPs
             * */
            case CONNECT_PEER:
                if(!container.getPeersList().isEmpty()){
                    this.peersConnectionFactory.createConnections(message);
                }
                break;
        }
    }
}
