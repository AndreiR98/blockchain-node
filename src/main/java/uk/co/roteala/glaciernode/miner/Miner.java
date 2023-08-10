package uk.co.roteala.glaciernode.miner;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.*;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.glaciernode.p2p.BrokerConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.security.ECKey;
import uk.co.roteala.security.utils.CryptographyUtils;
import uk.co.roteala.utils.BlockchainUtils;


import java.math.BigDecimal;
import java.math.BigInteger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Miner implements Mining {
    private final StorageServices storage;
    //private final Processor processor;

    @Value("${roteala.blockchain.miner-private-key}")
    private String minerPrivateKey;

    private final BrokerConnectionStorage brokerConnectionStorage;
    private final ClientConnectionStorage clientConnectionStorage;
    private final ServerConnectionStorage serverConnectionStorage;

    public final MiningWorker worker;

    private boolean isMining = true;
    private boolean hasMining = false;

    //Starts the node
    @Override
    @Bean
    public void start() throws InterruptedException {
        //Check if any

        ECKey privateKey = new ECKey(minerPrivateKey);
        Block minedBlock = new Block();
        BigInteger maxNonce = new BigInteger("ffffffff", 16);

        boolean isMining = false;
        BigInteger nonce = BigInteger.ZERO;
        long startTime = 0;
        List<PseudoTransaction> pseudoTransactions = new ArrayList<>();



        while(true) {
            //log.info("Worker:{}", worker);
            if (worker.isBrokerConnected()
                    && worker.isHasDataSync()
                    && worker.isHasMempoolData()
                    && worker.isHasStateSync()) {

                //Check the storage for transactions
                if(!storage.getPseudoTransactions().isEmpty()) {
                    ChainState state = storage.getStateTrie();

                    Coin reward = state.getReward();
                    Integer difficulty = state.getTarget();

                    if(!isMining) {
                        log.info("===Start mining===");

                        isMining = true;
                        nonce = BigInteger.ZERO;
                        startTime = System.currentTimeMillis();

                        pseudoTransactions =
                                storage.getPseudoTransactionGrouped(startTime);
                    }

                    //TODO: Implement multi queueing block mining

                    //Get the previous block
                    Block prevBlock = (state.getLastBlockIndex() - 1) <= 0 ? state.getGenesisBlock()
                            : storage.getBlockByIndex(state.getLastBlockIndex());

                    //Compute the new block
                    BlockMetadata newBlock = blockFromTemplate(pseudoTransactions, prevBlock, nonce, startTime, privateKey, reward, difficulty);

                    if(BlockchainUtils.computedTargetValue(newBlock.getBlock().getHash(), difficulty)){

                        log.info("===Block mined:{}===", newBlock.getBlock());

                        storage.addBlockMempool(newBlock.getBlock().getHash(), newBlock.getBlock());

                        MessageWrapper messageWrapper = new MessageWrapper();
                        messageWrapper.setType(MessageTypes.BLOCKHEADER);
                        messageWrapper.setAction(MessageActions.MINED_BLOCK);
                        messageWrapper.setVerified(true);
                        messageWrapper.setContent(newBlock.getBlock().getHeader());

                        log.info("Mem:{}", newBlock.getTransactionHashes());
                        updateMempoolTransactions(newBlock.getMempoolTransaction());

                        //Sent new mined block to broker
                        brokerConnectionStorage.getConnection()
                                .outbound().sendObject(Mono.just(messageWrapper.serialize()))
                                .then()
                                .subscribe();

                        //Send new mined block to each node
//                        clientConnectionStorage.getClientConnections()
//                                        .forEach(connection -> {
//                                            connection.outbound().sendObject(Mono.just(messageWrapper));
//                                        });
//
//                        serverConnectionStorage.getServerConnections()
//                                        .forEach(connection -> {
//                                            connection.outbound().sendObject(Mono.just(messageWrapper));
//                                        });



                        //updateMempoolTransactions(newBlock.getTransactions());
                        isMining = false;
                    }

                    nonce = nonce.add(BigInteger.ONE);
                } else {
                    isMining = false;
                    Thread.sleep(1000);
                }

                if(nonce.compareTo(maxNonce) > 0) {
                    nonce = BigInteger.ZERO;
                    startTime += 1000;
                }
            }

        }
    }

    private void updateMempoolTransactions(List<String> mempoolHash) {
        for(String hash : mempoolHash) {
            PseudoTransaction pseudoTransaction = storage.getMempoolTransactionByKey(hash);

            if(pseudoTransaction != null) {
                pseudoTransaction.setStatus(TransactionStatus.PROCESSED);

                storage.addMempool(pseudoTransaction.getPseudoHash(), pseudoTransaction);
            }
        }
    }

    private BlockMetadata blockFromTemplate(List<PseudoTransaction> mempoolTransactions,
                                    Block prevBlock, BigInteger nonce,
                                    long timeStamp, ECKey privateKey, Coin reward, Integer difficulty) {

        List<String> mempoolHashes = new ArrayList<>();
        List<String> transactionsHashes = new ArrayList<>();

        int i = 0;
        while (i < mempoolTransactions.size()) {
            PseudoTransaction mempoolTransaction = mempoolTransactions.get(i);

            String mapHash = BlockchainUtils.mapHashed(mempoolTransaction, (prevBlock.getHeader().getIndex() + 1), timeStamp, i);
            String trueHash = mapHash.split("_")[1];

            mempoolHashes.add(mapHash.split("_")[0]);
            transactionsHashes.add(trueHash);

            i++;
        }

        final String markleRoot = BlockchainUtils.markleRootGenerator(transactionsHashes);

        //Create header
        BlockHeader blockHeader = new BlockHeader();
        blockHeader.setVersion(0x16);
        blockHeader.setIndex(prevBlock.getHeader().getIndex() + 1);
        blockHeader.setPreviousHash(prevBlock.getHeader().getHash());
        blockHeader.setTimeStamp(timeStamp);
        blockHeader.setNonce(nonce.toString(16));
        blockHeader.setDifficulty(difficulty);
        blockHeader.setMinerAddress(privateKey.getPrivateKey().toAddress());
        blockHeader.setReward(reward);
        blockHeader.setMarkleRoot(markleRoot);
        blockHeader.setNumberOfTransactions(transactionsHashes.size());
        blockHeader.setHash();

        Block newBlock = new Block();
        newBlock.setHeader(blockHeader);

        newBlock.setConfirmations(1);
        newBlock.setStatus(BlockStatus.MINED);
        newBlock.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");
        newBlock.setTransactions(transactionsHashes);
        newBlock.setNumberOfBits(SerializationUtils.serialize(newBlock).length);
        //newBlock.setTransactions(mempoolHashes);

        return new BlockMetadata(newBlock, mempoolHashes, transactionsHashes);
    }


    //Stops
    @Override
    public void stop() {
        this.isMining = false;
    }


    @Data
    @AllArgsConstructor
    private class BlockMetadata {
        private Block block;
        private List<String> mempoolTransaction;
        private List<String> transactionHashes;
    }
}
