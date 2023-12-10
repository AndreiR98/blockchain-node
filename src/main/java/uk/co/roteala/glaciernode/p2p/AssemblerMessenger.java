package uk.co.roteala.glaciernode.p2p;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import uk.co.roteala.common.BasicModel;
import uk.co.roteala.common.events.MessageTemplate;
import uk.co.roteala.common.messenger.*;

import java.util.function.Function;

@Slf4j
public class AssemblerMessenger implements Function<Message, MessageTemplate> {
    private final Cache<String, MessageContainer> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .build();

    @Override
    public MessageTemplate apply(Message message) {
        try {
            MessageContainer container = cache.get(message.getMessageId(), k -> new MessageContainer());

            log.info("Message:{}", message);

            if (message.getMessageType() == MessageType.EMPTY) {
                return MessageTemplate.DEFAULT;
            }

            if (message.getMessageType() == MessageType.KEY) {
                log.info("Setting key for container: {}", message.getMessageId());
                container.setKey((MessageKey) message);
            }

            if (message.getMessageType() == MessageType.CHUNK) {
                log.info("Adding chunk for container: {}", message.getMessageId());
                container.addChunk((MessageChunk) message);
            }

            log.info("Aggregate status: {}", container.canAggregate());
            if (container.canAggregate()) {
                log.info("Start aggregating container!");
                return container.aggregate();
            }
        } catch (Exception e) {
            // Handle the exception appropriately, logging or rethrowing if necessary
            e.printStackTrace();
        }

        return MessageTemplate.DEFAULT;
    }
}
