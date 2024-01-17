package uk.co.roteala.glaciernode.broker;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;
import uk.co.roteala.common.messenger.*;
import uk.co.roteala.glaciernode.configs.StateManager;
import uk.co.roteala.net.ConnectionsStorage;

import java.util.ArrayList;
import java.util.List;


/**
 * Handles the broker transmission incoming of messages are forwarded to the processor
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerTransmissionHandler implements Handler<AsyncResult<NetSocket>> {
    @Autowired
    private ConnectionsStorage connectionStorage;

    @Autowired
    private Sinks.Many<Message> sink;

    @Autowired
    private Sinks.Many<MessageTemplate> messageTemplateSink;

    @Autowired
    private StateManager stateManager;

    private Buffer transmissionBuffer = Buffer.buffer();

    /**
     * Handles the successful or failed connection attempt to a broker.
     * @param event The result of the connection attempt.
     */
    @Override
    public void handle(AsyncResult<NetSocket> event) {
        if(event.succeeded()) {
            NetSocket netSocket = event.result();
            this.connectionStorage.setBrokerConnection(netSocket);
            this.stateManager.setBrokerConnected(true);
            log.info("Connection with broker: {} established!", netSocket.remoteAddress().hostAddress());

            messageTemplateSink.tryEmitNext(MessageTemplate.builder()
                            .eventAction(EventActions.REQUEST_SYNC)
                            .group(ReceivingGroup.BROKER)
                            .eventType(EventTypes.STATECHAIN)
                    .build());

            messageTemplateSink.tryEmitNext(MessageTemplate.builder()
                            .eventAction(EventActions.REQUEST)
                            .group(ReceivingGroup.BROKER)
                            .eventType(EventTypes.PEERS)
                    .build());

            netSocket.handler(new HandleIncomingBrokerData()
                    .processWithConnection(netSocket));

            netSocket.closeHandler(close -> {
                log.info("Connection with broker stopped!");
                this.connectionStorage.setBrokerConnection(null);
                this.stateManager.setBrokerConnected(false);
            });
        } else if (event.failed()) {
            log.error("Failed to connect to broker!");
        }
    }

    private class HandleIncomingBrokerData implements Handler<Buffer> {

        private NetSocket connection;

        public HandleIncomingBrokerData processWithConnection(NetSocket netSocket) {
            this.connection = netSocket;
            return this;
        }

        /**
         * Handles incoming data from the broker. If the data is processable, it is deserialized and emitted.
         * Otherwise, it's appended to the transmission buffer for further processing.
         * @param event The incoming data buffer.
         */
        @Override
        public void handle(Buffer event) {
            synchronized (transmissionBuffer) {
                transmissionBuffer.appendBuffer(event);
            }
            processAfter();
        }

        /**
         * Processes the accumulated data in the transmission buffer after new data has been added.
         * It attempts to deserialize each delimited part of the buffer into a message.
         */
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

                if (message != null) {
                    message.setOwner(this.connection);
                    message.setHandler(HandlerType.BROKER);
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

        /**
         * Determines if the incoming data is processable, based on the presence of a newline character at the end.
         * @param event The incoming data buffer.
         * @return True if the data is processable, false otherwise.
         */
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
}
