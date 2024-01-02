package uk.co.roteala.glaciernode.processor;

import io.reactivex.rxjava3.functions.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import uk.co.roteala.common.messenger.MessageTemplate;

@Slf4j
@RequiredArgsConstructor
public class IncomingMessageSorter implements Consumer<Flux<MessageTemplate>> {
    private final BrokerMessageProcessor brokerMessageProcessor;
    @Override
    public void accept(Flux<MessageTemplate> messageTemplateFlux) {
        messageTemplateFlux .parallel()
                .runOn(Schedulers.parallel())
                .doOnNext(messageTemplate -> {
                    switch (messageTemplate.getHandler()) {
                        case BROKER:
                            //brokerMessageProcessor.push(messageTemplate);
                            break;
                        // Other cases (CLIENT, SERVER) can be added here
                    }
                })
                .doOnError(e -> {
                    // Handle errors that occur during processing
                    System.err.println("Error in processing pipeline: " + e.getMessage());
                })
                .then()
                .subscribe();
    }
}
