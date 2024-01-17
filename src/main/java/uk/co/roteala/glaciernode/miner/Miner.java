package uk.co.roteala.glaciernode.miner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;
import uk.co.roteala.common.*;
import uk.co.roteala.common.messenger.*;
import uk.co.roteala.common.storage.ColumnFamilyTypes;
import uk.co.roteala.common.storage.Operator;
import uk.co.roteala.common.storage.SearchField;
import uk.co.roteala.common.storage.StorageTypes;
import uk.co.roteala.exceptions.MiningException;
import uk.co.roteala.exceptions.errorcodes.MiningErrorCode;
import uk.co.roteala.glaciernode.configs.NodeConfigs;
import uk.co.roteala.glaciernode.configs.StateManager;
import uk.co.roteala.glaciernode.storage.Storages;
import uk.co.roteala.security.ECKey;
import uk.co.roteala.utils.BlockchainUtils;
import uk.co.roteala.utils.Constants;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class Miner {

    private final Storages storages;
    private final NodeConfigs nodeConfigs;
    private final StateManager stateManager;
    private final Sinks.Many<MessageTemplate> messageTemplateSink;

    private BigInteger nonce = BigInteger.ZERO;
    private Instant startTime = Instant.now();
    private ECKey privateKey;
    private final AtomicBoolean isWorking = new AtomicBoolean(false);

    public Miner createThreadMiner() throws InterruptedException {
        this.privateKey = new ECKey(this.nodeConfigs.getMinerPrivateKey());
        AtomicBoolean isPaused = this.stateManager.getPauseMining();

        while (true) {
            if (!stateManager.allowMinerToWork()) {
                Thread.sleep(3000); // Sleep for 3 seconds before the next iteration
                continue;
            } else {
                if(this.stateManager.getPauseMining().get()) {
                    Thread.sleep(3000);
                    continue;
                }

                if(!this.stateManager.getPauseMining().get()) {
                    List<MempoolTransaction> transactions = fetchMempoolTransactions();

                    if(!isWorking.get()) {
                        setupMining();
                    }

                    //Check if the blockchain allows empty blocks mining
                    if(this.stateManager.getChainState().isAllowEmptyMining()) {
                        if(transactions.isEmpty()) {
                            mineEmptyBlock();
                        } else {
                            mineBlock(transactions);
                        }
                    } else {
                        if(transactions.isEmpty()) {
                            resetMining();
                            Thread.sleep(3000);
                        } else {
                            mineBlock(transactions);
                        }
                    }
                }

                if (nonce.compareTo(new BigInteger("ffffffff", 16)) > 0) {
                    resetMining();
                    isWorking.set(false); // Set the flag indicating that the miner is no longer working
                }
            }
        }
    }

    private List<MempoolTransaction> fetchMempoolTransactions() {
        Object fetched =  storages.getStorage(StorageTypes.MEMPOOL)
                .withCriteria(Operator.LTEQ, startTime.toEpochMilli(), SearchField.TIME_STAMP)
                .withHandler(ColumnFamilyTypes.TRANSACTIONS)
                .reversed(false)
                .page(100)
                .asBasicModel();

        List<MempoolTransaction> transactions = (List<MempoolTransaction>) fetched;
        log.info("Transactions: {}", transactions);

        return transactions;
    }

    private void mineBlock(List<MempoolTransaction> mempoolTransactions) {
        Block newBlock;
        Integer difficulty = stateManager.getChainState().getTarget();
        Block prevBlock = getPreviousBlock();

        isWorking.set(true);
        lockTransactions(mempoolTransactions);
        newBlock = generateBlock(prevBlock.getHash(), prevBlock.getHeader().getIndex(), mempoolTransactions);

        if (newBlock != null && (BlockchainUtils.computedTargetValue(newBlock.getHash(), difficulty))) {
            stateManager.getPauseMining().set(true);
            processMinedBlock(newBlock);
            return;
        }

        nonce = nonce.add(BigInteger.ONE);
    }

    private Block getPreviousBlock() {
        return (stateManager.getChainState().getLastBlockIndex()) <= 0 ? Constants.GENESIS_BLOCK
                : (Block) storages.getStorage(StorageTypes.BLOCKCHAIN)
                .get(ColumnFamilyTypes.BLOCKS, stateManager.getChainState().getLastBlockIndex().toString()
                        .getBytes(StandardCharsets.UTF_8));
    }

    private void setupMining() {
        nonce = BigInteger.ZERO;
        startTime = Instant.now();
    }

    private void resetMining() {
        nonce = BigInteger.ZERO;
        startTime = Instant.now();
    }

    private Block generateBlock(String previousHash, Integer index, List<MempoolTransaction> mempoolTransactions) {
        final int blockIndex = (index + 1);

        List<String> transactionHashes = new ArrayList<>();

        BlockHeader blockHeader = new BlockHeader();
        blockHeader.setMinerAddress(privateKey.getPublicKey().toAddress());
        blockHeader.setDifficulty(stateManager.getChainState().getTarget());
        blockHeader.setNonce(nonce.toString(16));
        blockHeader.setPreviousHash(previousHash);
        blockHeader.setVersion(0x16);
        blockHeader.setIndex(blockIndex);
        blockHeader.setTimeStamp(startTime.toEpochMilli());
        blockHeader.setBlockTime(System.currentTimeMillis());

        Block block = new Block();
        block.setHeader(blockHeader);

        if (!mempoolTransactions.isEmpty()) {
            int transactionIndex = 0;
            for (MempoolTransaction mempoolTransaction : mempoolTransactions) {
                mempoolTransaction.toTransaction(block, transactionIndex);
                transactionHashes.add(mempoolTransaction.getHash());

                transactionIndex++;
            }

            blockHeader.setNumberOfTransactions(transactionHashes.size());
        } else {
            blockHeader.setNumberOfTransactions(0);
        }

        String markleRoot = Constants.DEFAULT_HASH;

        if (BlockchainUtils.markleRootGenerator(transactionHashes) != null) {
            markleRoot = BlockchainUtils.markleRootGenerator(transactionHashes);
        }

        blockHeader.setMarkleRoot(markleRoot);
        blockHeader.setHash();
        block.setTransactions(transactionHashes);
        block.setConfirmations(1);
        block.setForkHash(Constants.DEFAULT_HASH);
        block.setStatus(BlockStatus.MINED);
        block.setNumberOfBits(SerializationUtils.serialize(block).length);
        block.setTransactions(transactionHashes);

        return block;
    }

    private void mineEmptyBlock() {
        isWorking.set(true);
        Integer difficulty = stateManager.getChainState().getTarget();
        Block prevBlock = getPreviousBlock();

        BlockHeader blockHeader = new BlockHeader();
        blockHeader.setMinerAddress(privateKey.getPublicKey().toAddress());
        blockHeader.setDifficulty(difficulty);
        blockHeader.setNonce(nonce.toString(16));
        blockHeader.setNumberOfTransactions(0);
        blockHeader.setPreviousHash(prevBlock.getHash());
        blockHeader.setVersion(0x16);
        blockHeader.setIndex(prevBlock.getHeader().getIndex() + 1);
        blockHeader.setTimeStamp(startTime.toEpochMilli());
        blockHeader.setBlockTime(System.currentTimeMillis());
        blockHeader.setMarkleRoot(Constants.DEFAULT_HASH);
        blockHeader.setHash();

        Block block = new Block();
        block.setTransactions(new ArrayList<>());
        block.setConfirmations(1);
        block.setForkHash(Constants.DEFAULT_HASH);
        block.setHeader(blockHeader);
        block.setStatus(BlockStatus.MINED);
        block.setNumberOfBits(SerializationUtils.serialize(block).length);

        if (block != null && (BlockchainUtils.computedTargetValue(block.getHash(), difficulty))) {
            stateManager.getPauseMining().set(true);
            processMinedBlock(block);
            return;
        }

        nonce = nonce.add(BigInteger.ONE);
    }

    private void processMinedBlock(Block newBlock) {
        try {
            stateManager.getPauseMining().set(true);

            if (storages.getStorage(StorageTypes.BLOCKCHAIN).has(ColumnFamilyTypes.BLOCKS, newBlock.getKey())) {
                throw new MiningException(MiningErrorCode.BLOCK_NOT_FOUND);
            }

            storages.getStorage(StorageTypes.MEMPOOL)
                    .putIfAbsent(true, ColumnFamilyTypes.BLOCKS, newBlock.getKey(), newBlock);
            log.info("Newly mined block added to the storage:{}", newBlock);

            messageTemplateSink.tryEmitNext(MessageTemplate.builder()
                    .message(newBlock)
                    .eventAction(EventActions.MINED_BLOCK)
                    .withOut(null)
                    .group(ReceivingGroup.ALL)
                    .eventType(EventTypes.BLOCK)
                    .build());
        } catch (Exception e) {
            log.error("Error on processing block:{}", e.getMessage());
        }
    }

    private void lockTransactions(List<MempoolTransaction> mempoolTransactions) {
        try {
            for (MempoolTransaction mempoolTransaction : mempoolTransactions) {
                mempoolTransaction.setStatus(TransactionStatus.LOCKED);

                storages.getStorage(StorageTypes.MEMPOOL)
                        .modify(ColumnFamilyTypes.TRANSACTIONS, mempoolTransaction.getKey(), mempoolTransaction);
            }
        } catch (Exception e) {
            log.error("Error while locking transactions:{}", e.toString());
        }
    }
}
