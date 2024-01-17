package uk.co.roteala.glaciernode.p2p;

import io.reactivex.rxjava3.functions.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Schedulers;
import uk.co.roteala.common.messenger.HandlerType;
import uk.co.roteala.common.messenger.Message;
import uk.co.roteala.common.messenger.MessageTemplate;
import uk.co.roteala.glaciernode.broker.BrokerMessageProcessor;
import uk.co.roteala.glaciernode.client.ClientMessageProcessor;
import uk.co.roteala.glaciernode.server.ServerMessageProcessor;

/**
 * Sorts the messages from 3 differents flows then assign 3 subscribers to differents flows
 * */
@Slf4j
@RequiredArgsConstructor
public class MessagesSorter implements Consumer<Flux<Message>> {
    private final AssemblerMessenger assemblerMessenger;
    private final BrokerMessageProcessor brokerMessageProcessor;
    private final ClientMessageProcessor clientMessageProcessor;
    private final ServerMessageProcessor serverMessageProcessor;

    @Override
    public void accept(Flux<Message> incomingMessages) {
        incomingMessages
                .parallel()
                .runOn(Schedulers.parallel())
                .map(this.assemblerMessenger)
                .flatMap(optionalTemplate -> optionalTemplate.map(Mono::just).orElseGet(Mono::empty))
                .doOnNext(this::processMessage)
                .then().subscribe();
    }

    private void processMessage(MessageTemplate messageTemplate) {
        // Route messages to appropriate processors based on the handler type
        if (messageTemplate.getHandler() == HandlerType.BROKER) {
            brokerMessageProcessor.accept(Mono.just(messageTemplate));
        } else if (messageTemplate.getHandler() == HandlerType.CLIENT) {
            clientMessageProcessor.accept(Mono.just(messageTemplate));
        } else if (messageTemplate.getHandler() == HandlerType.SERVER) {
            serverMessageProcessor.accept(Mono.just(messageTemplate));
        }
    }
}
