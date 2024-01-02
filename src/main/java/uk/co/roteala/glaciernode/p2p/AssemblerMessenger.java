package uk.co.roteala.glaciernode.p2p;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.co.roteala.common.messenger.*;

import java.util.Optional;
import java.util.function.Function;

@Slf4j
public class AssemblerMessenger implements Function<Message, Optional<MessageTemplate>> {
    @Autowired
    private Cache<String, MessageContainer> cache;

    @Override
    public Optional<MessageTemplate> apply(Message message) {
        try {
            MessageContainer container = cache.get(message.getMessageId(), k -> new MessageContainer());

            switch (message.getMessageType()) {
                case EMPTY:
                    cache.invalidate(message.getMessageId());
                    return Optional.empty();
                case KEY:
                    container.setKey((MessageKey) message);
                    break;
                case CHUNK:
                    container.addChunk((MessageChunk) message);
                    break;
            }

            if (container.canAggregate()) {
                cache.invalidate(message.getMessageId());
                return Optional.of(container.aggregate());
            }
        } catch (Exception e) {
            log.error("Error in message assembly: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }
}
