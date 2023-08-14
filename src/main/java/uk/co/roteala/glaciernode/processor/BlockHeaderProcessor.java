package uk.co.roteala.glaciernode.processor;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.netty.Connection;
import uk.co.roteala.common.*;
import uk.co.roteala.common.monetary.AmountDTO;
import uk.co.roteala.common.monetary.Fund;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.exceptions.MiningException;
import uk.co.roteala.exceptions.errorcodes.MiningErrorCode;
import uk.co.roteala.glaciernode.miner.Mining;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.utils.BlockchainUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@AllArgsConstructor
@NoArgsConstructor
public class BlockHeaderProcessor {
    private BlockHeader blockHeader;

    @Autowired
    private StorageServices storage;

    @Autowired
    private MoveFund moveFund;

    @Autowired
    private MiningWorker worker;

    /**
     * Append the block to the chain store and update the state
     * */
    public void processAppendBlockHeader() {
        try {
            if(this.blockHeader == null) {
                throw new MiningException(MiningErrorCode.BLOCK_NOT_FOUND);
            }

            Block memBlock = this.storage.getPseudoBlockByHash(this.blockHeader.getHash());

            if(memBlock == null) {
                throw new MiningException(MiningErrorCode.MINED_BLOCK_EMPTY);
            }

            List<String> transactionHashes = new ArrayList<>();

            Block block = new Block();
            block.setHeader(this.blockHeader);
            block.setConfirmations(memBlock.getConfirmations());
            block.setForkHash(memBlock.getForkHash());
            block.setStatus(BlockStatus.MINED);

            //Execute funds
            if(!memBlock.getTransactions().isEmpty()) {

                int index = 0;
                for(String pseudoHash : memBlock.getTransactions()) {
                    PseudoTransaction pseudoTransaction = this.storage.getMempoolTransactionByKey(pseudoHash);

                    Transaction transaction = BlockchainUtils
                            .mapPsuedoTransactionToTransaction(pseudoTransaction, this.blockHeader, index);

                    index++;

                    transactionHashes.add(transaction.getHash());

                    AccountModel sourceAccount = storage.getAccountByAddress(transaction.getFrom());

                    Fund fund = Fund.builder()
                            .sourceAccount(sourceAccount)
                            .isProcessed(true)
                            .amount(AmountDTO.builder()
                                    .rawAmount(transaction.getValue())
                                    .fees(transaction.getFees())
                                    .build())
                            .build();

                    moveFund.execute(fund);

                    storage.addTransaction(transaction.getHash(), transaction);

                    storage.deleteMempoolTransaction(pseudoHash);
                }

                block.setTransactions(transactionHashes);
                block.setNumberOfBits(SerializationUtils.serialize(block).length);
            } else {
                block.setTransactions(new ArrayList<>());
                block.setNumberOfBits(SerializationUtils.serialize(block).length);
            }

            Fund rewardFund = Fund.builder()
                    .sourceAccount(null)
                    .targetAccountAddress(this.blockHeader.getMinerAddress())
                    .isProcessed(true)
                    .amount(AmountDTO.builder()
                            .rawAmount(this.blockHeader.getReward())
                            .build())
                    .build();

            moveFund.executeRewardFund(rewardFund);

            ChainState state = this.storage.getStateTrie();
            state.setLastBlockIndex(state.getLastBlockIndex() + 1);

            NodeState nodeState = this.storage.getNodeState();
            nodeState.setLastBlockIndex(nodeState.getLastBlockIndex() + 1);
            nodeState.setUpdatedAt(System.currentTimeMillis());

            storage.addBlock(String.valueOf(block.getHeader().getIndex()), block);
            log.info("New block added to the chain:{}", block.getHash());

            storage.updateStateTrie(state);
            log.info("State updated with latest index:{}", state.getLastBlockIndex());

            storage.updateNodeState(nodeState);
            log.info("Node state updated:{}", nodeState);

            storage.deleteMempoolBlocksAtIndex(block.getHeader().getIndex());
            log.info("Deleted all mem blocks for index:{}", block.getHeader().getIndex());

            this.worker.setPauseMining(false);

        } catch (Exception e) {
            log.error("Error while appending new block!", e.getMessage());
        }
    }

    /**
     * Process the blocks that sync the chain
     * */
    public void processSyncBlock() {}

    private void updateMempoolTransactions(List<String> mempoolHashes, TransactionStatus status){
        for(String pseudoHash : mempoolHashes) {
            PseudoTransaction pseudoTransaction = storage.getMempoolTransactionByKey(pseudoHash);

            if(pseudoTransaction != null) {
                pseudoTransaction.setStatus(status);

                storage.addMempool(pseudoTransaction.getPseudoHash(), pseudoTransaction);
            }
        }
    }
}
