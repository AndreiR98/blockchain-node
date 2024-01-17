package uk.co.roteala.glaciernode.server;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;
import uk.co.roteala.common.messenger.HandlerType;
import uk.co.roteala.common.messenger.Message;
import uk.co.roteala.common.messenger.MessengerUtils;
import uk.co.roteala.glaciernode.client.ClientTransmissionHandler;
import uk.co.roteala.net.ConnectionsStorage;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerTransmissionHandler implements Handler<Buffer> {
    @Autowired
    private Sinks.Many<Message> sink;

    private NetSocket connection;

    public ServerTransmissionHandler processWithConnection(NetSocket netSocket) {
        this.connection = netSocket;
        return this;
    }

    private Buffer transmissionBuffer = Buffer.buffer();
    @Override
    public void handle(Buffer event) {
        synchronized (transmissionBuffer) {
            transmissionBuffer.appendBuffer(event);
        }

        processAfter();
    }

    private synchronized void processAfter() {
        String bufferContent = transmissionBuffer.toString();
        List<Integer> delimitersPosition = findDelimiters(bufferContent, MessengerUtils.delimiter.charAt(0));
        processBufferContent(bufferContent, delimitersPosition);
        updateTransmissionBuffer(delimitersPosition);
    }

    private List<Integer> findDelimiters(String content, char delimiter) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == delimiter) {
                positions.add(i);
            }
        }
        return positions;
    }

    private void processBufferContent(String content, List<Integer> delimiters) {
        int lowerBound = 0;
        for (Integer delimiterPos : delimiters) {
            String partition = content.substring(lowerBound, delimiterPos);
            Message message = MessengerUtils.deserialize(partition);
            message.setOwner(this.connection);
            message.setHandler(HandlerType.SERVER);

            if (message != null) {
                sink.tryEmitNext(message);
            }
            lowerBound = delimiterPos + 1;
        }
    }

    private void updateTransmissionBuffer(List<Integer> delimitersPosition) {
        if (!delimitersPosition.isEmpty()) {
            int lastProcessedPos = delimitersPosition.get(delimitersPosition.size() - 1) + 1;
            transmissionBuffer = Buffer.buffer(transmissionBuffer.getBytes(lastProcessedPos,
                    transmissionBuffer.length()));
        }
    }

    @Deprecated
    private synchronized boolean isProcessable(Buffer event) {
        if (event.length() > 0) {
            String bufferContent = event.toString();
            int newlineIndex = bufferContent.indexOf('\n');
            return newlineIndex != -1 && newlineIndex == bufferContent.length() - 1;
        }
        return false;
    }

    private synchronized Message processSingle(Buffer buffer) {
        int delimiterPosition = buffer.toString().indexOf(MessengerUtils.delimiter);
        String wrapperString = buffer.getString(0, delimiterPosition);

        return MessengerUtils.deserialize(wrapperString);
    }
}
