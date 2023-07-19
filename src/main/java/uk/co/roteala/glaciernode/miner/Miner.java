package uk.co.roteala.glaciernode.miner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.SerializationUtils;
import uk.co.roteala.common.*;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.glaciernode.processor.Processor;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.security.ECKey;
import uk.co.roteala.utils.BlockchainUtils;


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Miner implements Mining {
    private final StorageServices storage;
    private final Processor processor;

    @Value("${roteala.blockchain.miner-private-key}")
    private String minerPrivateKey;

    private boolean isMining = true;
    private boolean hasMining = false;

    //Starts the node
    @Override
    @Bean
    public void start() {
        //Check if any

        ECKey privateKey = new ECKey(minerPrivateKey);
        Block minedBlock = new Block();

        boolean isMining = true;

        do {
            /**
             * 1.Search the storage if any transactions
             * 2.If storage not empty retrieve pseudoTransaction
             * 3.Start mining the block
             * */
            boolean targetMatched = false;

            if(false){
                log.info("Mining started!");
                //Retrieve 120 random transaction
                //TODO:When fees implemented, add sorting based on the fees too.
                List<String> pseudoTransactionList = new ArrayList<>();
                //ChainState chainState = storage.getStateTrie();

                int miningTargetValue = 2;

                //If no blocks then it will return genesis block
                //Block previousBlock = storage.getBlockByIndex(chainState.getLastBlockIndex());

                Block previousBlock = new Block();
                previousBlock.setHash("0000000000000000000000000000000000000000000000000000000000000000");

                List<String> pseudoTransactionMatches = new ArrayList<>();

                Block pseudoBlock = new Block();
                pseudoBlock.setMiner("Rotaru 'Roti' Andrei");
                pseudoBlock.setIndex(0);
                pseudoBlock.setDifficulty(miningTargetValue);
                pseudoBlock.setPreviousHash(previousBlock.getHash());
                pseudoBlock.setReward(Coin.ZERO);
                pseudoBlock.setConfirmations(1);
                pseudoBlock.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");
                pseudoBlock.setStatus(BlockStatus.MINED);
                pseudoBlock.setVersion(0x16);
                pseudoBlock.getTimeStamp();

                int index = 0;

                for (String pseudoHash : pseudoTransactionList) {
                    PseudoTransaction pseudoTransaction = storage.getMempoolTransactionByKey(pseudoHash);

                    pseudoTransactionMatches.add(BlockchainUtils.mapHashed(pseudoTransaction, pseudoBlock, index));
                    index++;
                }

                //Order then alphabetically
                Collections.sort(pseudoTransactionMatches);

//                String markleRoot = BlockchainUtils
//                        .markleRootGenerator(BlockchainUtils
//                                .splitPseudoHashMatches(pseudoTransactionMatches)
//                                .get(1));

                //Prepare the pseudoBlock
                pseudoBlock.setMarkleRoot("0000000000000000000000000000000000000000000000000000000000000000");
                pseudoBlock.setTransactions(pseudoTransactionMatches);

                BigInteger maxNonce = new BigInteger("ffffffff", 16);
                BigInteger nonce = BigInteger.ZERO;

                long timeStamp = System.currentTimeMillis();

                //Mine the block
                do {
                    if(nonce.compareTo(maxNonce) == 0) {
                        nonce = BigInteger.ZERO;
                        timeStamp = System.currentTimeMillis();
                    }

                    nonce = nonce.add(BigInteger.ONE);

                    pseudoBlock.setTimeStamp(timeStamp);
                    pseudoBlock.setNonce(nonce.toString(16));
                    pseudoBlock.setNumberOfBits(SerializationUtils.serialize(pseudoBlock).length);
                    pseudoBlock.setHash(pseudoBlock.computeHash());

                    String proposedHash = pseudoBlock.getHash();

                    if(BlockchainUtils.computedTargetValue(proposedHash, miningTargetValue)) {
                        minedBlock = pseudoBlock;
                        log.info("Mined block:{}", minedBlock);
                        targetMatched = true;
                        break;
                    }
                } while (!targetMatched);

            }

        } while (isMining);
    }

    //Stops
    @Override
    public void stop() {
        this.isMining = false;
    }
}
