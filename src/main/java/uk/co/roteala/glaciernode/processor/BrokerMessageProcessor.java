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

        log.info("Worker:{}", this.worker);
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
                //processMessageAccount(message);
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


                    Fund fund = Fund.builder()
                            .targetAccountAddress(transaction.getTo())
                            .isProcessed(true)
                            .sourceAccount(sourceAccount)
                            .amount(AmountDTO.builder()
                                    .rawAmount(transaction.getValue())
                                    .fees(transaction.getFees())
                                    .build())
                            .build();

                    moveFund.reverseFunding(fund);
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


        switch (messageAction) {
            case APPEND:
                try {
                    if(block == null) {
                        throw new MiningException(MiningErrorCode.BLOCK_NOT_FOUND);
                    }

                    state.setLastBlockIndex(state.getLastBlockIndex() + 1);
                    nodeState.setUpdatedAt(System.currentTimeMillis());
                    nodeState.setRemainingBlocks(nodeState.getRemainingBlocks() - 1);

                    if(nodeState.getRemainingBlocks() == 0) {
                        this.worker.setHasDataSync(true);
                    }

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
                new BlockHeaderProcessor(blockHeader, storage, moveFund, worker, connection);

        switch (messageAction) {
            case APPEND_MINED_BLOCK:
                blockHeaderProcessor.processAppendBlockHeader();
                break;
            case DISCARD:
                blockHeaderProcessor.processDiscardBlock();
                break;
            case VERIFY:
                blockHeaderProcessor.processVerify();
                break;
        }
    }

    private void processMessageStateChain(Message message) {
        MessageActions action = message.getMessageAction();
        ChainState state = (ChainState) message.getMessage();

        final StateChainProcessor stateChainProcessor =
                new StateChainProcessor(clientConnectionStorage, storage, peersConnectionFactory, worker, state, connection);

        switch (action) {
            case APPEND:
                stateChainProcessor.processAppend();
                break;
            case MODIFY:
                stateChainProcessor.processModify();
                break;
        }
    }

    private void processMessageMempool(Message message) {
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

        final PeersMessageProcessor peersMessageProcessor =
                new PeersMessageProcessor(storage, worker, connection);

        switch (action) {
            case REQUEST_SYNC:
               if(!container.getPeersList().isEmpty()) {
                   for(String ip : container.getPeersList()) {
                       Peer peer = new Peer();
                       peer.setActive(true);
                       peer.setAddress(ip);
                       peer.setPort(7331);
                       peer.setLastTimeSeen(System.currentTimeMillis());

                       this.storage.addPeer(peer);

                       this.peersConnectionFactory.createConnection(peer);
                   }
               }
                break;
            case NO_CONNECTIONS:
                peersMessageProcessor.processNoConnections();
                break;
        }
    }

}
