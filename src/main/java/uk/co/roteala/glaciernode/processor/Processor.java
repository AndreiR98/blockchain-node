package uk.co.roteala.glaciernode.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.SerializationUtils;
import uk.co.roteala.common.BaseModel;
import uk.co.roteala.common.Block;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.events.*;
import uk.co.roteala.exceptions.MessageSerializationException;
import uk.co.roteala.exceptions.errorcodes.MessageSerializationErrCode;

public interface Processor {
    public void process(Message message);
    default Message mapper(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        try {
            String messageWrapperString = SerializationUtils.deserialize(bytes);
            ReferenceCountUtil.release(byteBuf);
            // Convert bytes to String

            ObjectMapper objectMapper = new ObjectMapper();
            MessageWrapper messageWrapper = objectMapper.readValue(messageWrapperString, MessageWrapper.class);

            MessageTemplate.MessageTemplateBuilder templateBuilder = MessageTemplate.builder()
                    .verified(messageWrapper.getVerified())
                    .messageAction(messageWrapper.getAction())
                    .content(messageWrapper.getContent())
                    .type(messageWrapper.getType());

            return templateBuilder.build();

        } catch (Exception e) {
            return defaultMessage();
        }
    }

    default Message defaultMessage() {
        return MessageTemplate.builder()
                .type(MessageTypes.DEFAULT)
                .messageAction(MessageActions.DEFAULT)
                .verified(ValidationType.FALSE)
                .content(null)
                .build();
    }
}
