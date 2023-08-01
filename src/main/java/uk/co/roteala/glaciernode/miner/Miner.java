package uk.co.roteala.glaciernode.miner;

import io.netty.buffer.Unpooled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import uk.co.roteala.common.*;
import uk.co.roteala.common.events.BlockMessage;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.glaciernode.p2p.BrokerConnectionStorage;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.security.ECKey;
import uk.co.roteala.utils.BlockchainUtils;



import java.math.BigInteger;

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



        while(true) {
            if (worker.isCanMine()) {
                if(!storage.getPseudoTransactions().isEmpty()) {
                    ChainState state = storage.getStateTrie();

                    if(!isMining) {
                        log.info("Start mining...");

                        isMining = true;
                        nonce = BigInteger.ZERO;
                        startTime = System.currentTimeMillis();
                    }

                    List<PseudoTransaction> pseudoTransactions = storage.getPseudoTransactions();

                    /**
                     * Check the blocks in the mempool, for each block existing use that as the previous block
                     * */

                    //Compute the new block
                    Block newBlock = blockFromTemplate(pseudoTransactions, state, nonce, startTime, privateKey);

                    if(BlockchainUtils.computedTargetValue(newBlock.getHash(), state.getTarget())){

                        BlockMessage blockMessage = new BlockMessage(newBlock);
                        blockMessage.setMessageAction(MessageActions.MINED_BLOCK);
                        blockMessage.setVerified(false);


                        storage.addBlockMempool(newBlock.getHash(), newBlock);

                        //Sent new mined block to broker
                        brokerConnectionStorage.getConnection()
                                .outbound().sendObject(Mono.just(
                                        Unpooled.copiedBuffer(SerializationUtils.serialize(blockMessage))))
                                .then()
                                .subscribe();

                        //Send new mined block to each node

                        log.info("Block mined:{}", newBlock);
                        updateMempoolTransactions(pseudoTransactions);
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

    private void updateMempoolTransactions(List<PseudoTransaction> mempoolHash) {
        mempoolHash.forEach(transaction -> {

            transaction.setStatus(TransactionStatus.PROCESSED);

            storage.addMempool(transaction.getPseudoHash(), transaction);
        });
    }

    private Block blockFromTemplate(List<PseudoTransaction> mempoolTransactions,
                                    ChainState chainState, BigInteger nonce, long timeStamp, ECKey privateKey) {

        List<String> mappedHashes = new ArrayList<>();
        List<String> transactions = new ArrayList<>();

        Block newBlock = new Block();

        newBlock.setMiner(privateKey.getPublicKey().toAddress());
        newBlock.setNonce(nonce.toString(16));
        newBlock.setDifficulty(chainState.getTarget());
        newBlock.setReward(chainState.getReward());
        newBlock.setIndex(chainState.getLastBlockIndex() + 1);
        newBlock.setTimeStamp(timeStamp);
        newBlock.setConfirmations(1);
        newBlock.setVersion(0x16);
        newBlock.setStatus(BlockStatus.MINED);
        newBlock.setPreviousHash(storage.getBlockByIndex(chainState.getLastBlockIndex()).getHash());
        newBlock.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");

        mempoolTransactions.forEach(mempoolTransaction -> {
            int i = 0;
            final String mapHash = BlockchainUtils.mapHashed(mempoolTransaction, newBlock, i);
            final String trueHash = mapHash.split("_")[1];

            mappedHashes.add(mapHash);
            transactions.add(trueHash);
            i++;
        });

        newBlock.setTransactions(transactions);
        newBlock.setMarkleRoot(BlockchainUtils.markleRootGenerator(transactions));
        newBlock.setNumberOfBits(SerializationUtils.serialize(newBlock).length);
        newBlock.setHash(newBlock.computeHash());
        newBlock.setTransactions(mappedHashes);

        return newBlock;
    }


    //Stops
    @Override
    public void stop() {
        this.isMining = false;
    }
}
