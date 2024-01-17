package uk.co.roteala.glaciernode.broker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import uk.co.roteala.common.*;
import uk.co.roteala.common.messenger.*;
import uk.co.roteala.common.storage.ColumnFamilyTypes;
import uk.co.roteala.common.storage.StorageTypes;
import uk.co.roteala.glaciernode.client.ClientInitializer;
import uk.co.roteala.glaciernode.configs.StateManager;
import uk.co.roteala.glaciernode.p2p.AssemblerMessenger;
import uk.co.roteala.glaciernode.storage.Storages;
import uk.co.roteala.utils.BlockchainUtils;
import uk.co.roteala.utils.Constants;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Process messages coming from broker
 * Can send to:
 * Other servers, other clients
 * */
@Slf4j
@RequiredArgsConstructor
public class BrokerMessageProcessor implements Consumer<Mono<MessageTemplate>> {
    private final Storages storages;

    private final ClientInitializer clientInitializer;

    private final StateManager manager;

    private final Sinks.Many<MessageTemplate> messageTemplateSink;

    @Override
    public void accept(Mono<MessageTemplate> message) {
        message.doOnNext(this::internalMessageTemplateProcessor)
                .subscribe();
    }

    private void internalMessageTemplateProcessor(MessageTemplate messageTemplate) {
        if(messageTemplate.getHandler() == HandlerType.BROKER) {
            switch (messageTemplate.getEventType()) {
                case MEMPOOL_TRANSACTION:
                    processMempoolEvent(messageTemplate);
                    break;
                case PEERS:
                    processPeerEvent(messageTemplate);
                    break;
                case STATECHAIN:
                    processStateChain(messageTemplate);
                    break;
                case BLOCK:
                    processBlockEvent(messageTemplate);
                    break;
            }
        }
    }

    private void processBlockEvent(MessageTemplate messageTemplate) {
        switch (messageTemplate.getEventAction()) {
            case VERIFY:
                this.manager.getPauseMining().set(true);

                final Block block = (Block) messageTemplate.getMessage();

                if (block.isValidBlock(this.storages.getStorage(StorageTypes.MEMPOOL), this.manager.getTarget())
                            && !this.storages.getStorage(StorageTypes.BLOCKCHAIN)
                            .has(ColumnFamilyTypes.BLOCKS, block.getKey())) {

                    this.storages.getStorage(StorageTypes.MEMPOOL)
                                    .putIfAbsent(true, ColumnFamilyTypes.BLOCKS, block.getHash()
                                            .getBytes(StandardCharsets.UTF_8), block);

                    log.info("Successfully verified proposed block: {}", block.getHash());

                    messageTemplateSink.tryEmitNext(MessageTemplate.builder()
                            .message(Response.builder()
                                    .type(EventTypes.BLOCK)
                                    .location(block.getHash())
                                    .status(true)
                                    .build())
                            .eventType(EventTypes.BLOCK)
                            .eventAction(EventActions.RESPONSE)
                            .group(ReceivingGroup.BROKER)
                            .build());
                } else {
                    log.error("Failed to verify block at index: {} with hash: {}", block.getIndex(), block.getHash());
                }
                break;
            case APPEND:
                Block finalBlock = (Block) messageTemplate.getMessage();

                log.info("Status: {}", finalBlock.isValidBlock(this.storages.getStorage(StorageTypes.MEMPOOL), this.manager.getTarget()));

                if (finalBlock.isValidBlock(this.storages.getStorage(StorageTypes.MEMPOOL), this.manager.getTarget())
                        && !(this.storages.getStorage(StorageTypes.BLOCKCHAIN)
                        .has(ColumnFamilyTypes.BLOCKS, finalBlock.getKey()))) {

                    this.storages.getStorage(StorageTypes.BLOCKCHAIN)
                            .put(true, ColumnFamilyTypes.BLOCKS, finalBlock.getKey(), finalBlock);

                    this.storages.getStorage(StorageTypes.MEMPOOL)
                            .delete(ColumnFamilyTypes.BLOCKS, finalBlock.getHash().getBytes(StandardCharsets.UTF_8));

                    ChainState state = this.manager.getChainState();
                    state.setLastBlockIndex(state.getLastBlockIndex() + 1);

                    this.storages.getStorage(StorageTypes.STATE)
                            .modify(ColumnFamilyTypes.STATE, Constants.DEFAULT_STATE_NAME
                                    .getBytes(StandardCharsets.UTF_8), state);

                    this.manager.getPauseMining().set(false);

                    log.info("New block: {} appended to the chain", finalBlock.getHash());
                    log.info("Chain status updated: {}", state);
                } else {
                    log.error("Failed to append block at index: {} with hash: {}", finalBlock.getIndex(), finalBlock.getHash());
                }
                break;
        }
    }

    private void processStateChain(MessageTemplate messageTemplate) {
        switch (messageTemplate.getEventAction()) {
            case RESPONSE:
                Response response = (Response) messageTemplate.getMessage();

                final ChainState state = (ChainState) response.getPayload();

                this.storages.getStorage(StorageTypes.STATE)
                        .modify(ColumnFamilyTypes.STATE, Constants.DEFAULT_STATE_NAME
                                .getBytes(StandardCharsets.UTF_8), state);
                log.info("State chain status updated!");
                break;
        }
    }

    private void processPeerEvent(MessageTemplate messageTemplate) {
        log.info("V:{}", messageTemplate);
        switch (messageTemplate.getEventAction()) {
            case RESPONSE:
                Response response = (Response) messageTemplate.getMessage();
                final PeersContainer peersContainer = (PeersContainer) response.getPayload();

                log.info("Peer container: {}", peersContainer);

                Flux.fromIterable(peersContainer.getPeersList())
                        .doOnNext(peer -> {
                            log.info("Creating connection with: {}", peer);
                            this.clientInitializer.connect(peer);
                        })
                        .doFinally(signalType -> {
                            if(signalType == SignalType.ON_COMPLETE) {
                                //Check how many connections
                                //Check if status are matching last block, reward, all of that
                                this.manager.verifyState();
                            }
                        })
                        .then()
                        .subscribe();
                break;
        }
    }

    private void processMempoolEvent(MessageTemplate messageTemplate) {
        switch (messageTemplate.getEventAction()) {
            case VERIFY:
                final MempoolTransaction mempoolTransaction = (MempoolTransaction) messageTemplate
                        .getMessage();

                if(mempoolTransaction.verifyTransaction()) {
                    //Response to broker
                    messageTemplateSink.tryEmitNext(MessageTemplate.builder()
                            .eventType(EventTypes.MEMPOOL_TRANSACTION)
                            .group(ReceivingGroup.BROKER)
                            .message(Response.builder()
                                    .location(mempoolTransaction.getHash())
                                    .type(EventTypes.MEMPOOL_TRANSACTION)
                                    .status(true)
                                    .build())
                            .eventAction(EventActions.RESPONSE)
                            .build());
                }
                break;
            case APPEND:
                final MempoolTransaction mempoolTrans = (MempoolTransaction) messageTemplate
                        .getMessage();

                storages.getStorage(StorageTypes.MEMPOOL)
                        .putIfAbsent(true, mempoolTrans.getKey(), mempoolTrans);
                log.info("New mempool transaction ready to be mined: {}", mempoolTrans);

                //Process virtual balances
                break;
        }
    }
}

