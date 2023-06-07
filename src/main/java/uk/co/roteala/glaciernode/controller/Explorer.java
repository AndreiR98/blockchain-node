package uk.co.roteala.glaciernode.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.roteala.common.*;
import uk.co.roteala.common.controllers.AddressResponse;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.security.ECKey;

import java.math.BigDecimal;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;
import java.util.Objects;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/explorer")
public class Explorer {
    private final StorageServices storage;


    /**
     * Fetch account details from the storage
     * Query every transaction that matches account address
     * */
    @GetMapping("/addresses/{address}")
    public ResponseEntity<AddressResponse> getAddressDetails(@PathVariable(value = "address") String address) {
        AddressResponse account = new AddressResponse();

        List<String> transactions = storage.getAddressTransactions(address);

        log.info("Transactions:{}", transactions);

        Coin balance = Coin.ZERO;

        //Compute the balance


        account.setTransactions(transactions);
        account.setAddressBalance(new BigDecimal(balance.getValue()));

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(account, headers, HttpStatus.OK);
    }

    @GetMapping("/transaction/test")
    public ResponseEntity<String> sendTransaction() throws Exception {
        TransactionBaseModel tx = new TransactionBaseModel();

        UTXO in = new UTXO();
        in.setCoinbase(true);
        in.setAddress(null);
        in.setValue(null);
        in.setPubKeyScript("b20d4f2141768c75c59fa74291b0034288d913d601e2dc8ec678abdafa8be4de");
        in.setSigScript(null);
        in.setTxid("0000000000000000000000000000000000000000000000000000000000000000");

        UTXO out = new UTXO();
        out.setCoinbase(true);
        out.setAddress("16wRoKZXiETVRKvuucy4NFnYbiiKZ8ZUSx");
        out.setPubKeyScript("b20d4f2141768c75c59fa74291b0034288d913d601e2dc8ec678abdafa8be4de");
        out.setValue(Coin.valueOf(100));
        out.setSpender(null);
        out.setSpent(false);
        out.setTxid(tx.hashHeader());


        tx.setBlockHash("d0bdfc7e6ede0d0dca9724909feb0f2a767f0f9780a6b1e7282709b4c58aaeef");
        tx.setBlockNumber(0);
        tx.setFees(Coin.ZERO);
        tx.setVersion(0x10);
        tx.setTransactionIndex(0);
        tx.setTimeStamp(System.currentTimeMillis());
        tx.setConfirmations(1);
        tx.setBlockNumber(0);
        tx.setTo("16wRoKZXiETVRKvuucy4NFnYbiiKZ8ZUSx");
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setHash(tx.hashHeader());

        BaseBlockModel block = new BaseBlockModel();
        block.setTimeStamp(System.currentTimeMillis());
        block.setIndex(0);
        block.setHash("d0bdfc7e6ede0d0dca9724909feb0f2a767f0f9780a6b1e7282709b4c58aaeef");

        storage.addTransaction(tx.getHash(), tx);
        storage.addBlock(block.getIndex().toString(), block);

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(tx.getHash(), headers, HttpStatus.OK);
    }

    /**Fetch block details based in the number
     * */
//    @GetMapping("/block/{number}")
//    public ResponseEntity<> getBlockDetails(@PathVariable(value = "number") String index){}
//
//    /**
//     * Fetch transaction details based on the hash
//     * */
    @GetMapping("/transaction/{hash}")
    public ResponseEntity<TransactionBaseModel> getTransactionDetails(@PathVariable(value = "hash") String hash) throws RocksDBException {
        TransactionBaseModel tx = null;

        log.info("Hash:{}", hash);

        if(storage.getTransactionByKey(hash) != null){
            tx = storage.getTransactionByKey(hash);
        }

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(tx, headers, HttpStatus.OK);
    }
//
//    /**
//     * Send transaction
//     * */
//    @PostMapping("/send")
//    @ResponseStatus(HttpStatus.OK)
//    public ResponseEntity<> sendTransactionToMemePool(@Valid @RequestBody TransactionSendRequest){}
}
