package uk.co.roteala.glaciernode.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import uk.co.roteala.common.Block;
import uk.co.roteala.common.messenger.*;
import uk.co.roteala.common.storage.ColumnFamilyTypes;
import uk.co.roteala.common.storage.StorageTypes;
import uk.co.roteala.glaciernode.configs.StateManager;
import uk.co.roteala.glaciernode.p2p.AssemblerMessenger;
import uk.co.roteala.glaciernode.storage.Storages;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Process messages coming from other clients, in this case node acts as a server for them
 * */
@Slf4j
@RequiredArgsConstructor
public class ServerMessageProcessor implements Consumer<Mono<MessageTemplate>> {
    private final Storages storages;

    private final StateManager manager;

    private final Sinks.Many<MessageTemplate> messageTemplateSink;
    @Override
    public void accept(Mono<MessageTemplate> message) {
        message.doOnNext(this::internalMessageTemplateProcessor)
                .subscribe();
    }

    private void internalMessageTemplateProcessor(MessageTemplate messageTemplate) {
        if(messageTemplate.getHandler() == HandlerType.SERVER) {
            switch (messageTemplate.getEventType()) {
                case BLOCK:
                    processBlockEvent(messageTemplate);
                    break;
            }
        }
    }

    private void processBlockEvent(MessageTemplate messageTemplate) {
        switch (messageTemplate.getEventAction()) {
            case MINED_BLOCK:
                this.manager.getPauseMining().set(true);

                Block newBlock = (Block) messageTemplate.getMessage();

                if(newBlock.isValidBlock(this.storages.getStorage(StorageTypes.MEMPOOL), this.manager.getTarget())) {
                    if(!this.storages.getStorage(StorageTypes.MEMPOOL).has(ColumnFamilyTypes.BLOCKS, newBlock.getHash()
                            .getBytes(StandardCharsets.UTF_8))) {
                        this.storages.getStorage(StorageTypes.MEMPOOL)
                                .putIfAbsent(true, ColumnFamilyTypes.BLOCKS, newBlock.getHash()
                                        .getBytes(StandardCharsets.UTF_8), newBlock);

                        log.info("Successfully verified proposed block: {}", newBlock.getHash());

                        messageTemplateSink.tryEmitNext(MessageTemplate.builder()
                                .group(ReceivingGroup.BROKER)
                                .eventAction(EventActions.RESPONSE)
                                .message(Response.builder()
                                        .status(true)
                                        .type(EventTypes.BLOCK)
                                        .location(newBlock.getHash())
                                        .build())
                                .eventType(EventTypes.BLOCK)
                                .build());

                        messageTemplateSink.tryEmitNext(MessageTemplate.builder()
                                        .group(ReceivingGroup.ALL)
                                        .withOut(List.of(messageTemplate.getOwner()))
                                        .message(newBlock)
                                        .eventType(EventTypes.BLOCK)
                                        .eventAction(EventActions.MINED_BLOCK)
                                .build());
                    } else {
                        log.info("Block: {} already verified!", newBlock.getHash());
                    }
                }
                break;
        }
    }
}
