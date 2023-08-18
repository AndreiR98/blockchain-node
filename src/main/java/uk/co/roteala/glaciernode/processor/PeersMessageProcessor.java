package uk.co.roteala.glaciernode.processor;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import uk.co.roteala.common.BlockHeader;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.NodeState;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.events.MessageWrapper;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.PeersConnectionFactory;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.time.Duration;

@Slf4j
@Component
@AllArgsConstructor
@NoArgsConstructor
public class PeersMessageProcessor {
    @Autowired
    private StorageServices storage;

    @Autowired
    private MiningWorker worker;

    private Connection brokerConnection;

    /**
     * Process the remaining blocks
     * */
    public void processNoConnections() {
        try {
            NodeState nodeState = this.storage.getNodeState();

            int currentNodeLastIndex = nodeState.getLastBlockIndex();

            if(nodeState.getRemainingBlocks() > 0) {
                Flux.range(currentNodeLastIndex, currentNodeLastIndex+nodeState.getRemainingBlocks())
                        .map(blockIndex -> {
                            BlockHeader blockHeader = new BlockHeader();
                            blockHeader.setIndex(blockIndex);

                            MessageWrapper messageWrapper = new MessageWrapper();
                            messageWrapper.setAction(MessageActions.REQUEST);
                            messageWrapper.setType(MessageTypes.BLOCKHEADER);
                            messageWrapper.setVerified(true);
                            messageWrapper.setContent(blockHeader);

                            return messageWrapper;
                        }).doOnNext(messageWrapper -> {
                            this.brokerConnection.outbound().sendObject(Mono.just(messageWrapper.serialize()))
                                    .then().delayElement(Duration.ofMillis(150)).subscribe();
                        }).then().subscribe();
            }

        } catch (Exception e) {
            log.error("Error during processing block requests!", e.getMessage());
        }
    }
}
