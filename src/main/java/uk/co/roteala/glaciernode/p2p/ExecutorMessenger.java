package uk.co.roteala.glaciernode.p2p;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.co.roteala.common.messenger.EventActions;
import uk.co.roteala.common.messenger.MessageContainer;
import uk.co.roteala.common.messenger.MessageTemplate;

import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
public abstract class ExecutorMessenger implements Consumer<MessageTemplate> {
    @Autowired
    private Cache<String, MessageContainer> cache;
    @Override
    public void accept(MessageTemplate messageTemplate) {
        if(messageTemplate.getEventAction() != EventActions.DEFAULT
                || messageTemplate.getEventAction() != EventActions.DISCARD) {
            log.info("Execute:{}", messageTemplate);
            //cache.invalidate(messageTemplate.getMessageId());
        }
    }
}
