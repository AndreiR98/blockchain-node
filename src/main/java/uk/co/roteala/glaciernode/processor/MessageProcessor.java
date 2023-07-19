package uk.co.roteala.glaciernode.processor;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import uk.co.roteala.common.Block;
import uk.co.roteala.common.PseudoTransaction;
import uk.co.roteala.common.events.BlockMessage;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.glaciernode.handlers.BrokerConnectionHandler;
import uk.co.roteala.glaciernode.p2p.PeerGroupConnections;
import uk.co.roteala.glaciernode.p2p.PeersConnections;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageProcessor implements Processor {
    private final StorageServices storage;

    @Autowired
    private List<Connection> connectionStorage;

    //Pass the processor in handler
    @Override
    public void process(NettyInbound inbound, NettyOutbound outbound) {
        inbound.receive().retain()
                .map(this::mapToMessage)
                .doOnNext(message -> {
                    inbound.withConnection(message::setConnection);

                    this.processMessage(message, outbound);
                }).then().subscribe();
    }
    //TODO:Implement checking for back-sending when the node will send the message back to the parent node, ie when mined a block
    /**
     * If node A mines a new block will broadcast to all peers including parent node
     * The parent node will broadcast the new mined block to all it's peers including back to A so that's a loop
     * When a node is broadcasting a block or transaction loop all Connections list and exclude the connectioon address in the message
     * When broadcasting to ALL basically send the original message if passed validation to all peers
     * */
    /**
     * Process the incoming message check type and action then either respond the all clients(broadcast) or single client(outbound)
     * */
    private void processMessage(Message message, NettyOutbound outbound) {
        MessageTypes type = message.getMessageType();
        MessageActions actions = message.getMessageAction();

        switch (type) {
            case ACCOUNT:
                processMessageAccount(message);
                break;
            case STATECHAIN:
                processStateChainMessage(message);
                break;
            case BLOCK:
                processBlockMessage(message);
                break;
            case TRANSACTION:
                processTransactionMessage(message);
                break;
            case MEMPOOL:
                processMempoolMessage(message);
                break;
            case PEERS:
                processPeersMessage(message);
                break;
            default:
                // Code to handle cases when type does not match any of the above
        }
    }

    private Message mapToMessage(ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        Message message = SerializationUtils.deserialize(bytes);
        ReferenceCountUtil.release(byteBuf);

        return message;
    }

    /**
     * Process account messages
     * Possible actions: VERIFY, ADD/MODIFY, SYNC
     *
     * For SYNC return all accounts
     * */
    private void processMessageAccount(Message message){}

    /**
     * Process state chain message
     * Possible actions: Modify/Add, Sync
     * */
    private void processStateChainMessage(Message message){}

    /**
     * Process block message
     * Possible actions: Add/Append, Modify, Sync
     *
     * For sync also send all transactions within the block
     * */
    private void processBlockMessage(Message message){}

    /**
     * Process transaction message
     * Possible actions: Add/Append, Modify(confirmations status),Verify
     * */
    private void processTransactionMessage(Message message){}

    /**
     * Process mempool transactions
     * Possible actions: Verify, Add, Modify, Delete
     * */
    private void processMempoolMessage(Message message) {}

    /**
     * Process peers message
     * Possible actions: Delete, Modify, Add
     * */
    private void processPeersMessage(Message message){}
}
