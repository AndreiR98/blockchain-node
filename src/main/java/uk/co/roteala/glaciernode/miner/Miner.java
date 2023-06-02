package uk.co.roteala.glaciernode.miner;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Point;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.co.roteala.common.*;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.security.ECKey;
import uk.co.roteala.security.utils.HashingFactory;


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.List;

@Slf4j
@Configuration
public class Miner {

    private ECKey ecKey() {
        return new ECKey("b4ba7e977fb42b3f3aa096702b068558c4a24906ebdb3b9eb689c6ccf4ea7735");
    }

    private String computePublicScript() {
        String script = HashingFactory.sha256Hash((ecKey().getPublicKey().getX() + ecKey().getPublicKey().getY()).getBytes()).toString();

        return script;
    }

    @Bean
    public void mine() throws NoSuchAlgorithmException, InvalidKeyException {
        UTXO in = new UTXO();
        in.setCoinbase(true);
        in.setAddress(null);
        in.setValue(null);
        in.setPubKeyScript(null);
        //in.setSigScript(ecKey(), null);
        in.setTxid("0000000000000000000000000000000000000000000000000000000000000000");

        UTXO out = new UTXO();
        out.setAddress(ecKey().getPublicKey().toAddress());
        out.setPubKeyScript(computePublicScript());
        out.setValue(Coin.valueOf(100));
        out.setSpender(null);
        out.setSpent(false);

        TransactionBaseModel tx = new TransactionBaseModel();
        tx.setBlockHash(null);
        tx.setBlockNumber(0);
        tx.setFees(Coin.ZERO);
        tx.setVersion(0x10);
        tx.setTransactionIndex(0);
        tx.setIn(List.of(in));
        tx.setOut(List.of(out));
        tx.setTimeStamp(System.currentTimeMillis());
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setHash(tx.hashHeader());

        BaseBlockModel block = new BaseBlockModel();
        block.setVersion(0x10);
        //block.setMarkleRoot(computeMarkleRoot(tx.getHash()));
        block.setTimeStamp(System.currentTimeMillis());
        block.setDifficulty(BigInteger.ONE);
        block.setNonce(null);
        block.setPreviousHash("0000000000000000000000000000000000000000000000000000000000000000");
        //block.setNumberOfBits(computeNumberOfBytes(block));
        block.setTransactions(List.of(tx.getHash()));
        block.setIndex(0);
        block.setConfirmations(1);
        block.setStatus(BlockStatus.MINED);
        block.setMiner(ecKey().getPublicKey().toAddress());
        block.setHash(block.getHash());

        log.info("Block:{}", block);
        log.info("Transaction:{}", tx);
        log.info("Signature:{}", returnSignature(in, ecKey()));
    }


    private String returnSignature(UTXO utxo, ECKey ecKey) throws NoSuchAlgorithmException, InvalidKeyException {
//        final String utxoHash = utxo.hashHeader();
//        final String algorithm = "HmacSHA256";
//
//        //Signature structure:
//        //signature_r signature_s
//
//        ECNamedCurveParameterSpec ecParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1");
//
//        ECPoint ecPoint = ecParameterSpec.getG();
//
//        log.info("Secret:{}", ecKey.getPrivateKey().getD());
//
//        BigInteger secretKey = ecKey.getPrivateKey().getD();
//
//        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.toByteArray(), algorithm);
//        Mac mac = Mac.getInstance(algorithm);
//        mac.init(secretKeySpec);
//
//        byte[] hmacBytes = mac.doFinal(utxoHash.getBytes());
//
//        final ECPoint ecP = ecPoint.multiply(new BigInteger(bytesToHexString(hmacBytes), 16))
//                .normalize();
//
//        final BigInteger c = secretKey.multiply(ecP.getAffineXCoord().toBigInteger())
//                .mod(ecParameterSpec.getN())
//                .add(new BigInteger(utxoHash, 16));
//
//        final String s = (secretKey.modInverse(ecParameterSpec.getN())
//                .multiply(c))
//                .mod(ecParameterSpec.getN()).toString();
//
//        log.info("Secret:{}", ecP.getAffineXCoord().toBigInteger());
//
//        final BigInteger cc = new BigInteger(s, 16)
//                .modInverse(ecParameterSpec.getN());
//
//        final BigInteger u1 = (new BigInteger(utxoHash, 16)
//                .multiply(cc))
//                .mod(ecParameterSpec.getN());
//
//        final BigInteger u2 = (ecP.getAffineXCoord().toBigInteger()
//                .multiply(cc))
//                .mod(ecParameterSpec.getN());
//
//        ECFieldElement xFieldElement = ecParameterSpec.getCurve().fromBigInteger(ecP.getAffineXCoord().toBigInteger());
//        ECFieldElement yFieldElement = ecParameterSpec.getCurve().fromBigInteger(new BigInteger(s, 16));
//
//        final ECPoint p = new FixedPointCombMultiplier()
//                .multiply(ecP.getCurve().createPoint(xFieldElement.toBigInteger().mod(ecP.getCurve().getOrder()), yFieldElement.toBigInteger()), BigInteger.ONE);
//
//        final ECPoint p1 = p.multiply(u1);
//
//        final ECPoint p2 = ecParameterSpec.getG().multiply(u2);
//
//        final BigInteger v = p1.add(p2).getAffineXCoord().toBigInteger();
//
//        log.info("Points:{}, {}", ecP.getAffineXCoord().toBigInteger(), v);
        return null;
    }

    private static String bytesToHexString(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = String.format("%02x", b);
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
