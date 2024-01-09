package uk.co.roteala.glaciernode.processor;

import com.github.benmanes.caffeine.cache.Cache;
import io.reactivex.rxjava3.functions.Consumer;
import io.vertx.core.net.NetSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import uk.co.roteala.common.MempoolTransaction;
import uk.co.roteala.common.messenger.*;
import uk.co.roteala.common.storage.StorageTypes;
import uk.co.roteala.glaciernode.client.ClientInitializer;
import uk.co.roteala.glaciernode.p2p.AssemblerMessenger;
import uk.co.roteala.glaciernode.p2p.ExecutorMessenger;
import uk.co.roteala.glaciernode.storage.Storages;
import uk.co.roteala.net.Peer;

/**
 * Process messages coming from broker
 * Can send to:
 * Other servers, other clients
 * */
@Slf4j
@RequiredArgsConstructor
public class BrokerMessageProcessor implements Consumer<Flux<Message>>{
    @Autowired
    private Storages storages;

    @Autowired
    private ClientInitializer clientInitializer;

    private final AssemblerMessenger assemblerMessenger;

    private final Sinks.Many<MessageTemplate> messageTemplateSink;

    @Override
    public void accept(Flux<Message> messageFlux) {
        messageFlux.parallel()
                .runOn(Schedulers.parallel())
                .map(this.assemblerMessenger)
                .flatMap(optionalTemplate -> optionalTemplate.map(Mono::just).orElseGet(Mono::empty))
                .doOnNext(this::internalMessageTemplateProcessor)
                .then().subscribe();
    }

    private void internalMessageTemplateProcessor(MessageTemplate messageTemplate) {
        switch (messageTemplate.getEventType()) {
            case MEMPOOL_TRANSACTION:
                processMempoolEvent(messageTemplate);
                break;
            case PEERS:
                processPeerEvent(messageTemplate);
                break;
        }
    }

    private void processPeerEvent(MessageTemplate messageTemplate) {
        switch (messageTemplate.getEventAction()) {
            case RESPONSE:
                Response response = (Response) messageTemplate.getMessage();
                final Peer peer = (Peer) response.getPayload();

                clientInitializer.connect(peer);

                break;
        }
    }

    private void processMempoolEvent(MessageTemplate messageTemplate) {
        switch (messageTemplate.getEventAction()) {
            case VERIFY_THEN_APPEND:
                final MempoolTransaction mempoolTransaction = (MempoolTransaction) messageTemplate
                        .getMessage();

                if(mempoolTransaction.verifyTransaction()) {
                    storages.getStorage(StorageTypes.MEMPOOL)
                            .putIfAbsent(true, mempoolTransaction.getKey(), mempoolTransaction);

                    //Update virtual balances

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
        }
    }
}

