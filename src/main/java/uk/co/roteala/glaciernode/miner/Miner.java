package uk.co.roteala.glaciernode.miner;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.Connection;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.*;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.exceptions.MiningException;
import uk.co.roteala.exceptions.errorcodes.MiningErrorCode;
import uk.co.roteala.glaciernode.p2p.BrokerConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.security.ECKey;
import uk.co.roteala.security.utils.CryptographyUtils;
import uk.co.roteala.utils.BlockchainUtils;


import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.BigInteger;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
public class Miner implements Mining {

    @Autowired
    private StorageServices storage;
    //private final Processor processor;

    @Value("${roteala.blockchain.miner-private-key}")
    private String minerPrivateKey;

    @Value("${roteala.blockchain.is-mining}")
    private boolean isMining;

    @Autowired
    private MiningWorker miningWorker;

    @Autowired
    private BrokerConnectionStorage brokerConnectionStorage;

    @Autowired
    private ClientConnectionStorage clientConnectionStorage;

    @Autowired
    private ServerConnectionStorage serverConnectionStorage;

    private ExecutorService threadPool = Executors.newFixedThreadPool(2);

    private BigInteger nonce = BigInteger.ZERO;
    private long startTime = 0;
    private ECKey privateKey;


    @PreDestroy
    public void shutdownThreadPool() {
        threadPool.shutdown();
    }

    @Override
    public void start() {
        this.isMining = true;
    }

    @Override
    public void stop() {
        this.isMining = false;
    }

    //Run the miner
    @Override
    @Bean
    public void work() {
        try {
            this.privateKey =  new ECKey(minerPrivateKey);
            while(true) {
                if(shouldStartMining()) {
                    if (!this.miningWorker.isPauseMining()) { // Check if mining is not paused
                        mineBlock();
                        //threadPool.submit(this::mineBlock);
                    }
                } else {
                    this.isMining = false;
                    Thread.sleep(3000);
                }

                if (nonce.compareTo(new BigInteger("ffffffff", 16)) > 0) {
                    resetMining(); // Fix the time after reaching max nonce
                }
            }
        } catch (Exception e) {
            log.error("Error while mining:{}", e);
        }
    }

    /**
     * When deploying to larger environments add check for children and parents
     * */
    private boolean shouldStartMining() {
        ChainState state = storage.getStateTrie();

        if(state != null) {
            if(state.isAllowEmptyMining()) {
                return this.miningWorker.isBrokerConnected()
                        && this.miningWorker.isHasStateSync();
            }

            return miningWorker.isBrokerConnected()
                    && miningWorker.isHasDataSync()
                    && miningWorker.isHasStateSync();
        }

        return false;
    }

    private void mineBlock() {
        ChainState state = storage.getStateTrie();
        Coin reward = state.getReward();
        Integer difficulty = state.getTarget();

        Block prevBlock = (state.getLastBlockIndex()) <= 0 ? state.getGenesisBlock()
                : storage.getBlockByIndex(state.getLastBlockIndex());

        if(!this.isMining) {
            setupMining();
        }

        List<PseudoTransaction> availablePseudoTransaction = storage
                .getPseudoTransactionGrouped(this.startTime);

        BlockMetadata newBlock;

        if(state.isAllowEmptyMining()
                && availablePseudoTransaction.isEmpty()) {
            newBlock = generateEmptyBlock(reward, difficulty, prevBlock.getHash(), prevBlock.getHeader().getIndex());
        } else if(!availablePseudoTransaction.isEmpty()) {
            newBlock = generateBlock(reward, difficulty, prevBlock.getHash(), prevBlock.getHeader().getIndex(), availablePseudoTransaction);
        } else {
            newBlock = null;
        }

        if(newBlock != null && (BlockchainUtils.computedTargetValue(newBlock.getBlock().getHash(), difficulty))) {
                this.miningWorker.setPauseMining(true);
                processMinedBlock(newBlock);
                log.info("====== STOP MINING =====");
                resetMining();

        }

        this.nonce = this.nonce.add(BigInteger.ONE);
    }

    private void setupMining() {
        log.info("===== START MINER ======");
        this.isMining = true;
        this.nonce = BigInteger.ZERO;

        this.startTime = System.currentTimeMillis();
    }

    private void resetMining() {
        log.info("===== START MINING NEW BLOCK ======");
        this.nonce = BigInteger.ZERO;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Generate an empty block without any transaction
     * */
    private BlockMetadata generateBlock(Coin reward, Integer target, String previousHash, Integer index, List<PseudoTransaction> pseudoTransactions) {

        final int blockIndex = (index + 1);

        List<String> pseudoHashes = new ArrayList<>();
        List<String> transactionHashes = new ArrayList<>();

        BlockHeader blockHeader = new BlockHeader();
        blockHeader.setReward(reward);
        blockHeader.setMinerAddress(this.privateKey.getPublicKey().toAddress());
        blockHeader.setDifficulty(target);
        blockHeader.setNonce(this.nonce.toString(16));
        blockHeader.setNumberOfTransactions(0);
        blockHeader.setPreviousHash(previousHash);
        blockHeader.setVersion(0x16);
        blockHeader.setIndex(blockIndex);
        blockHeader.setTimeStamp(this.startTime);
        blockHeader.setBlockTime(System.currentTimeMillis());



        for(PseudoTransaction pseudoTransaction : pseudoTransactions) {
            int transactionIndex = 0;

            Transaction transaction = BlockchainUtils
                    .mapPsuedoTransactionToTransaction(pseudoTransaction, blockHeader, transactionIndex);

            pseudoHashes.add(pseudoTransaction.getPseudoHash());
            transactionHashes.add(transaction.getHash());

            transactionIndex++;
        }

        String markleRoot = BlockchainUtils.markleRootGenerator(transactionHashes);

        blockHeader.setMarkleRoot(markleRoot);
        blockHeader.setHash();

        Block block = new Block();
        block.setTransactions(transactionHashes);
        block.setConfirmations(1);
        block.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");
        block.setHeader(blockHeader);
        block.setStatus(BlockStatus.MINED);
        block.setNumberOfBits(SerializationUtils.serialize(block).length);
        block.setTransactions(pseudoHashes);

        return new BlockMetadata(block, pseudoHashes, transactionHashes);
    }

    /**
     * Generate block containing transactions
     * */
    private BlockMetadata generateEmptyBlock(Coin reward, Integer target, String previousHash, Integer index) {
        BlockHeader blockHeader = new BlockHeader();
        blockHeader.setReward(reward);
        blockHeader.setMinerAddress(this.privateKey.getPublicKey().toAddress());
        blockHeader.setDifficulty(target);
        blockHeader.setNonce(this.nonce.toString(16));
        blockHeader.setNumberOfTransactions(0);
        blockHeader.setPreviousHash(previousHash);
        blockHeader.setVersion(0x16);
        blockHeader.setIndex(index + 1);
        blockHeader.setTimeStamp(this.startTime);
        blockHeader.setBlockTime(System.currentTimeMillis());
        blockHeader.setMarkleRoot("0000000000000000000000000000000000000000000000000000000000000000");
        blockHeader.setHash();

        Block block = new Block();
        block.setTransactions(new ArrayList<>());
        block.setConfirmations(1);
        block.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");
        block.setHeader(blockHeader);
        block.setStatus(BlockStatus.MINED);
        block.setNumberOfBits(SerializationUtils.serialize(block).length);

        return new BlockMetadata(block, null, null);
    }



    /**
     * Process the mined block
     * Send it to other peers for confirmation and to the broker
     * Add it to the memory pool while waiting
     * */
    private void processMinedBlock(BlockMetadata newBlock) {
        try {
            //Process transactional block
//            if(newBlock.getTransactionHashes().isEmpty()
//                    && newBlock.getMempoolTransaction().isEmpty()
//                    && Objects.equals(newBlock.getBlock()
//                    .getHeader().getMarkleRoot(), "0000000000000000000000000000000000000000000000000000000000000000")) {
//                //
//            } else {
//
//            }
            this.miningWorker.setPauseMining(true);

            MessageWrapper blockHeaderWrapper = new MessageWrapper();
            blockHeaderWrapper.setVerified(true);
            blockHeaderWrapper.setType(MessageTypes.BLOCKHEADER);
            blockHeaderWrapper.setAction(MessageActions.MINED_BLOCK);
            blockHeaderWrapper.setContent(newBlock.getBlock().getHeader());

            Block block = newBlock.getBlock();

            if(storage.getPseudoBlockByHash(block.getHash()) != null) {
                throw new MiningException(MiningErrorCode.ALREADY_EXISTS);
            }

            storage.addBlockMempool(block.getHash(), block);
            log.info("Newly mined block added to the storage:{}", block);

            //Send it to the broker
            brokerConnectionStorage.getConnection()
                    .outbound().sendObject(Mono.just(blockHeaderWrapper.serialize()))
                    .then().subscribe();

            List<Connection> clientConnections = this.clientConnectionStorage.getClientConnections();

            for(Connection connection : clientConnections) {
                connection.outbound()
                        .sendObject(Mono.just(blockHeaderWrapper.serialize()))
                        .then().subscribe();
            }

            List<Connection> serverConnections = this.serverConnectionStorage.getServerConnections();

            for(Connection connection : serverConnections) {
                connection.outbound()
                        .sendObject(Mono.just(blockHeaderWrapper.serialize()))
                        .then().subscribe();
            }
        } catch (Exception e) {
            log.error("Error on processing block:{}", e.getMessage());
        }
    }

    @Data
    @AllArgsConstructor
    private class BlockMetadata {
        private Block block;
        private List<String> mempoolTransaction;
        private List<String> transactionHashes;
    }
}
