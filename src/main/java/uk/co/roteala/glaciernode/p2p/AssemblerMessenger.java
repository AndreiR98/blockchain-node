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
            log.info("Id:{}", message);
            MessageContainer container = cache.get(message.getMessageId(), k -> new MessageContainer());

            if (message.getMessageType() == MessageType.EMPTY) {
                return Optional.empty();
            }

            if (message.getMessageType() == MessageType.KEY) {
                container.setKey((MessageKey) message);
            }

            if (message.getMessageType() == MessageType.CHUNK) {
                container.addChunk((MessageChunk) message);
            }

            if (container.canAggregate()) {
                return Optional.of(container.aggregate());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }
}
