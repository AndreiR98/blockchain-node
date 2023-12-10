package uk.co.roteala.glaciernode.p2p;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import uk.co.roteala.common.BasicModel;
import uk.co.roteala.common.events.MessageTemplate;

import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
public class ExecutorMessenger implements Consumer<MessageTemplate> {

    @Override
    public void accept(MessageTemplate messageTemplate) {
        log.info("Execute:{}", messageTemplate);
    }
}
