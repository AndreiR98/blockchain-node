//package uk.co.roteala.glaciernode.processor;
//
//import lombok.AllArgsConstructor;
//import lombok.NoArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.SerializationUtils;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//import reactor.core.publisher.Mono;
//import reactor.netty.Connection;
//import uk.co.roteala.common.*;
//import uk.co.roteala.common.events.MessageActions;
//import uk.co.roteala.common.events.MessageTypes;
//import uk.co.roteala.common.events.MessageWrapper;
//import uk.co.roteala.common.events.ValidationType;
//import uk.co.roteala.common.monetary.AmountDTO;
//import uk.co.roteala.common.monetary.Fund;
//import uk.co.roteala.common.monetary.MoveFund;
//import uk.co.roteala.exceptions.MiningException;
//import uk.co.roteala.exceptions.errorcodes.MiningErrorCode;
//import uk.co.roteala.glaciernode.miner.Mining;
//import uk.co.roteala.glaciernode.miner.MiningWorker;
//import uk.co.roteala.glaciernode.p2p.BrokerConnectionStorage;
//import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
//import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
//import uk.co.roteala.glaciernode.storage.StorageServices;
//import uk.co.roteala.utils.BlockchainUtils;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Objects;
//
//@Slf4j
//@Component
//@AllArgsConstructor
//@NoArgsConstructor
//public class BlockHeaderProcessor {
//    private BlockHeader blockHeader;
//
//    @Autowired
//    private StorageServices storage;
//
//    @Autowired
//    private MoveFund moveFund;
//
//    @Autowired
//    private MiningWorker worker;
//
//
//    private Connection brokerConnection;
//
//    /**
//     * Append the block to the chain store and update the state
//     * */
//    public void processAppendBlockHeader() {
//        try {
//            if(this.blockHeader == null) {
//                throw new MiningException(MiningErrorCode.BLOCK_NOT_FOUND);
//            }
//
//            Block memBlock = this.storage.getPseudoBlockByHash(this.blockHeader.getHash());
//
//            if(memBlock == null) {
//                MessageWrapper messageWrapper = new MessageWrapper();
//                messageWrapper.setAction(MessageActions.REQUEST);
//                messageWrapper.setType(MessageTypes.BLOCKHEADER);
//                messageWrapper.setContent(this.blockHeader);
//
//                this.brokerConnection.outbound()
//                        .sendObject(Mono.just(messageWrapper.serialize()))
//                        .then().subscribe();
//                log.info("Request block:{} from broker!", this.blockHeader.getIndex());
//            } else {
//                List<String> transactionHashes = new ArrayList<>();
//
//                Block block = new Block();
//                block.setHeader(this.blockHeader);
//                block.setConfirmations(memBlock.getConfirmations());
//                block.setForkHash(memBlock.getForkHash());
//                block.setStatus(BlockStatus.MINED);
//
//                //Execute funds
//                if(!memBlock.getTransactions().isEmpty()) {
//
//                    int index = 0;
//                    for(String pseudoHash : memBlock.getTransactions()) {
//                        PseudoTransaction pseudoTransaction = this.storage.getMempoolTransactionByKey(pseudoHash);
//
//                        Transaction transaction = BlockchainUtils
//                                .mapPsuedoTransactionToTransaction(pseudoTransaction, this.blockHeader, index);
//
//                        index++;
//
//                        transactionHashes.add(transaction.getHash());
//
//                        AccountModel sourceAccount = storage.getAccountByAddress(transaction.getFrom());
//
//                        Fund fund = Fund.builder()
//                                .sourceAccount(sourceAccount)
//                                .isProcessed(true)
//                                .targetAccountAddress(transaction.getTo())
//                                .amount(AmountDTO.builder()
//                                        .rawAmount(transaction.getValue())
//                                        .fees(transaction.getFees())
//                                        .build())
//                                .build();
//
//                        moveFund.execute(fund);
//
//                        storage.addTransaction(transaction.getHash(), transaction);
//
//                        storage.deleteMempoolTransaction(pseudoHash);
//                    }
//
//                    block.setTransactions(transactionHashes);
//                    block.setNumberOfBits(SerializationUtils.serialize(block).length);
//                } else {
//                    block.setTransactions(new ArrayList<>());
//                    block.setNumberOfBits(SerializationUtils.serialize(block).length);
//                }
//                Fund rewardFund = Fund.builder()
//                        .sourceAccount(null)
//                        .targetAccountAddress(this.blockHeader.getMinerAddress())
//                        .isProcessed(true)
//                        .amount(AmountDTO.builder()
//                                .rawAmount(this.blockHeader.getReward())
//                                .build())
//                        .build();
//
//                moveFund.executeRewardFund(rewardFund);
//
//                ChainState state = this.storage.getStateTrie();
//                state.setLastBlockIndex(state.getLastBlockIndex() + 1);
//
//                NodeState nodeState = this.storage.getNodeState();
//                nodeState.setLastBlockIndex(nodeState.getLastBlockIndex() + 1);
//                nodeState.setUpdatedAt(System.currentTimeMillis());
//
//                storage.addBlock(String.valueOf(block.getHeader().getIndex()), block);
//                log.info("New block added to the chain:{}", block.getHash());
//
//                storage.updateStateTrie(state);
//                log.info("State updated with latest index:{}", state.getLastBlockIndex());
//
//                storage.updateNodeState(nodeState);
//                log.info("Node state updated:{}", nodeState);
//
//                storage.deleteMempoolBlocksAtIndex(block.getHeader().getIndex());
//                log.info("Deleted all mem blocks for index:{}", block.getHeader().getIndex());
//
//                this.worker.setPauseMining(false);
//            }
//        } catch (Exception e) {
//            log.error("Error while appending new block!", e);
//        }
//    }
//
//    /**
//     * Discard the block when ordered by broker
//     * */
//    public void processDiscardBlock() {
//        try {
//            Block memBlock = this.storage.getPseudoBlockByHash(this.blockHeader.getHash());
//
//            updateMempoolTransactions(memBlock.getTransactions(), TransactionStatus.VALIDATED);
//
//            this.storage.deleteMempoolBlock(this.blockHeader.getHash());
//            log.info("Discarded success!");
//
//            this.worker.setPauseMining(false);
//        } catch (Exception e) {
//            log.error("Error while discarding block:{}", e.getMessage());
//        }
//    }
//
//    /**
//     * Process verify block request
//     * */
//    public void processVerify() {
//        try {
//           if(this.worker.isHasDataSync()){
//               MessageWrapper brokerWrapper = new MessageWrapper();
//               brokerWrapper.setAction(MessageActions.VERIFIED_MINED_BLOCK);
//               brokerWrapper.setContent(this.blockHeader);
//               brokerWrapper.setType(MessageTypes.BLOCKHEADER);
//               brokerWrapper.setVerified(tryToValidateBlock());
//
//               this.brokerConnection
//                       .outbound().sendObject(Mono.just(brokerWrapper.serialize()))
//                       .then().subscribe();
//
//               log.info("Valid block:{}", this.blockHeader.getHash());
//           } else {
//               log.error("Could not validate, data not sync!");
//               throw new MiningException(MiningErrorCode.OPERATION_FAILED);
//           }
//        } catch (Exception e) {
//            MessageWrapper brokerWrapper = new MessageWrapper();
//            brokerWrapper.setAction(MessageActions.VERIFIED_MINED_BLOCK);
//            brokerWrapper.setVerified(ValidationType.FALSE);
//            brokerWrapper.setContent(this.blockHeader);
//            brokerWrapper.setType(MessageTypes.BLOCKHEADER);
//
//            this.brokerConnection
//                    .outbound().sendObject(Mono.just(brokerWrapper.serialize()))
//                    .then().subscribe();
//
//            log.info("Invalid block:{}", this.blockHeader.getHash());
//        }
//    }
//
//    private ValidationType tryToValidateBlock() {
//        Block block = this.storage.getPseudoBlockByHash(this.blockHeader.getHash());
//
//        if(block == null) {
//            validateHeader();
//        } else {
//            return ValidationType.TRUE;
//        }
//
//        return ValidationType.FALSE;
//    }
//
//    private boolean validateHeader() {
//        try {
//            ChainState state = storage.getStateTrie();
//
//            Block prevBlock = state.getLastBlockIndex() <= 0 ? state.getGenesisBlock()
//                    : storage.getBlockByIndex(state.getLastBlockIndex());
//
//            if(!BlockchainUtils.computedTargetValue(this.blockHeader.getHash(), this.blockHeader.getDifficulty())) {
//                log.error("Target not matched!");
//                throw new MiningException(MiningErrorCode.PSEUDO_MATCH);
//            }
//
//            if(this.storage.getBlockByIndex(this.blockHeader.getIndex()) != null) {
//                log.error("Block already exists");
//                throw new MiningException(MiningErrorCode.ALREADY_EXISTS);
//            }
//
//            if(!Objects.equals(prevBlock.getHash(), this.blockHeader.getPreviousHash())) {
//                log.error("Prev hash failed");
//                throw new MiningException(MiningErrorCode.IN_BETWEEN);
//            }
//
//            if(!Objects.equals(this.blockHeader.getMarkleRoot(),
//                    "0000000000000000000000000000000000000000000000000000000000000000")) {
//                log.error("Markle root failed!");
//                return !matchMerkleRoot().isEmpty();
//            }
//
//            return true;
//        } catch (Exception e) {
//            log.error("Error while validating block!");
//        }
//        return false;
//    }
//
//
//    private List<String> matchMerkleRoot() {
//        List<PseudoTransaction> availablePseudoTransactions = this.storage
//                .getPseudoTransactionGrouped(this.blockHeader.getTimeStamp());
//
//        List<String> transactionHashes = new ArrayList<>();
//        List<String> pseudoHashes = new ArrayList<>();
//
//        for (int index = 0; index < availablePseudoTransactions.size(); index++) {
//            PseudoTransaction pseudoTransaction = availablePseudoTransactions.get(index);
//
//            Transaction transaction = BlockchainUtils
//                    .mapPsuedoTransactionToTransaction(pseudoTransaction, this.blockHeader, index);
//
//
//            transactionHashes.add(transaction.getHash());
//            pseudoHashes.add(pseudoTransaction.getPseudoHash());
//        }
//
//        String markleRoot = BlockchainUtils.markleRootGenerator(transactionHashes);
//        int totalSize = transactionHashes.size();
//
//        log.info("Tx:{}", transactionHashes);
//
//        try {
//            if (totalSize >= this.blockHeader.getNumberOfTransactions()) {
//                while (!markleRoot.equals(this.blockHeader.getMarkleRoot()) && !transactionHashes.isEmpty()) {
//                    //log.info("MR:{}", markleRoot);
//                    transactionHashes.remove(totalSize - 1); // Remove the last transaction hash
//                    pseudoHashes.remove(totalSize - 1);
//
//                    totalSize--;
//                    markleRoot = BlockchainUtils.markleRootGenerator(transactionHashes);
//                }
//            } else {
//                //This could not be possible
//                throw new MiningException(MiningErrorCode.OPERATION_FAILED);
//            }
//
//            if(markleRoot.equals(this.blockHeader.getMarkleRoot())) {
//                return pseudoHashes;
//            }
//        } catch (Exception e) {
//            log.error("Error:{}", e.getMessage());
//        }
//
//
//        return new ArrayList<>();
//    }
//
//    private void processEmptyBlock() {
//            ChainState state = storage.getStateTrie();
//
//            Block prevBlock = state.getLastBlockIndex() <= 0 ? state.getGenesisBlock()
//                    : storage.getBlockByIndex(state.getLastBlockIndex());
//
//            if(this.blockHeader == null) {
//                throw new MiningException(MiningErrorCode.BLOCK_NOT_FOUND);
//            }
//
//            if(this.storage.getPseudoBlockByHash(this.blockHeader.getHash()) != null) {
//                throw new MiningException(MiningErrorCode.ALREADY_EXISTS);
//            }
//
//            if(!Objects.equals(prevBlock.getHash(), this.blockHeader.getPreviousHash())){
//                log.info("Could not match with a previous hash:{} with:{}!", prevBlock.getHash(),
//                        this.blockHeader.getPreviousHash());
//                throw new MiningException(MiningErrorCode.PREVIOUS_HASH);
//            }
//    }
//
//    //Send confirmation to broker
//
//    private void updateMempoolTransactions(List<String> mempoolHashes, TransactionStatus status){
//        for(String pseudoHash : mempoolHashes) {
//            PseudoTransaction pseudoTransaction = storage.getMempoolTransactionByKey(pseudoHash);
//
//            if(pseudoTransaction != null) {
//                pseudoTransaction.setStatus(status);
//
//                storage.addMempool(pseudoTransaction.getPseudoHash(), pseudoTransaction);
//            }
//        }
//    }
//}
