//package uk.co.roteala.glaciernode.processor;
//
//
//import lombok.AllArgsConstructor;
//import lombok.NoArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//import reactor.netty.Connection;
//import uk.co.roteala.common.Block;
//import uk.co.roteala.common.NodeState;
//import uk.co.roteala.common.Transaction;
//import uk.co.roteala.common.events.MessageActions;
//import uk.co.roteala.common.events.MessageTypes;
//import uk.co.roteala.common.events.MessageWrapper;
//import uk.co.roteala.exceptions.SyncException;
//import uk.co.roteala.exceptions.errorcodes.SyncErrorCode;
//import uk.co.roteala.glaciernode.storage.StorageServices;
//
//import java.time.Duration;
//import java.util.ArrayList;
//
//@Slf4j
//@Component
//@AllArgsConstructor
//@NoArgsConstructor
//public class NodeStateProcessor {
//
//    @Autowired
//    private StorageServices storage;
//
//    private NodeState nodeState;
//
//    private Connection connection;
//
//    /**
//     * Send the client back all the transactions plus blocks
//     * */
//    public void processRequestSync() {
//        try {
//            NodeState currentNode = this.storage.getNodeState();
//
//            if(currentNode.getRemainingBlocks() > 0) {
//                /**
//                 * Respons with not possible ask borker
//                 * */
//                throw new SyncException(SyncErrorCode.SYNC_NOT_POSSIBLE);
//            }
//
//            Flux.range(nodeState.getLastBlockIndex()+1, this.storage.getStateTrie().getLastBlockIndex())
//                    .doOnNext(blockIndex -> {
//                        Block block = this.storage.getBlockByIndex(blockIndex);
//
//                        Flux<MessageWrapper> transactionWrapeprs = Flux.fromIterable(block.getTransactions())
//                                .map(transactionHash -> {
//                                    Transaction transaction = this.storage.getTransactionByKey(transactionHash);
//
//                                    MessageWrapper transactionWrapper = new MessageWrapper();
//                                    transactionWrapper.setType(MessageTypes.TRANSACTION);
//                                    transactionWrapper.setContent(transaction);
//                                    transactionWrapper.setAction(MessageActions.APPEND);
//
//                                    return transactionWrapper;
//                                })
//                                .delayElements(Duration.ofMillis(250));
//
//                        block.setTransactions(new ArrayList<>());
//
//                        MessageWrapper blockWrapper = new MessageWrapper();
//                        blockWrapper.setContent(block);
//                        blockWrapper.setType(MessageTypes.BLOCK);
//                        blockWrapper.setAction(MessageActions.APPEND);
//
//                        Mono.just(blockWrapper)
//                                .mergeWith(transactionWrapeprs)
//                                .doOnNext(wrapper -> this.connection.outbound()
//                                        .sendObject(wrapper.serialize())
//                                        .then().subscribe());
//                    }).then().subscribe();
//        }catch (Exception e) {
//            log.error("Failing to process request sync!{}", e.getMessage());
//        }
//    }
//}
