package uk.co.roteala.glaciernode.miner;

import lombok.extern.slf4j.Slf4j;
import uk.co.roteala.common.*;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.glaciernode.security.ECKey;
import uk.co.roteala.glaciernode.security.utils.HashingFactory;

import java.security.NoSuchAlgorithmException;
import java.util.List;

@Slf4j
public class Miner {

    private ECKey ecKey() {
        return new ECKey("b4ba7e977fb42b3f3aa096702b068558c4a24906ebdb3b9eb689c6ccf4ea7735");
    }

    private String computePublicScript() {
        String script = HashingFactory.sha256Hash((ecKey().getPublicKey().getX() + ecKey().getPublicKey().getY()).getBytes()).toString();

        return script;
    }

    private String computeSigScript() {
        //
    }
    public void mine() throws NoSuchAlgorithmException {
        UTXO in = new UTXO();
        in.setCoinbase(true);
        in.setSigScript(computeSigScript());
        in.setTxid("0000000000000000000000000000000000000000000000000000000000000000");

        UTXO out = new UTXO();
        out.setAddress("16wRoKZXiETVRKvuucy4NFnYbiiKZ8ZUSx");
        out.setPubKeyScript(computePublicScript());
        out.setValue(Coin.valueOf(100));
        out.setSpender(null);
        out.setSpent(false);

        TransactionBaseModel tx = new TransactionBaseModel();
        tx.setHash(tx.hashHeader());
        tx.setBlockHash(null);
        tx.setBlockNumber(0);
        tx.setFees(Coin.ZERO);
        tx.setVersion(0x10);
        tx.setTransactionIndex(0);
        tx.setIn(List.of(in));
        tx.setOut(List.of(out));
        tx.setTimeStamp(System.currentTimeMillis());
        tx.setStatus(TransactionStatus.SUCCESS);

        BaseBlockModel block = new BaseBlockModel();
        block.setVersion(0x10);
        block.setMarkleRoot(computeMarkleRoot(tx.getHash()));
        block.setTimeStamp(System.currentTimeMillis());

        block.setNonce(null);
        block.setPreviousHash("0000000000000000000000000000000000000000000000000000000000000000");
        block.setNumberOfBits(computeNumberOfBytes(block));
        block.setTransactions(List.of(tx.getHash()));
        block.setIndex(0);
        block.setConfirmations(1);
        block.setStatus(BlockStatus.MINED);
        block.setMiner("16wRoKZXiETVRKvuucy4NFnYbiiKZ8ZUSx");
        block.setHash(block.getHash());
    }
}
