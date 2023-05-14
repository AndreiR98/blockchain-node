package uk.co.roteala.glaciernode.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.SerializationUtils;
import org.springframework.web.bind.annotation.*;
import reactor.netty.Connection;
import uk.co.roteala.common.*;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.glaciernode.client.PeerCreatorFactory;
import uk.co.roteala.glaciernode.miner.Miner;
import uk.co.roteala.glaciernode.services.ConnectionServices;
import uk.co.roteala.glaciernode.storage.StorageCreatorComponent;
import uk.co.roteala.utils.GlacierUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/blockchain")
public class TxController {
    @Autowired
    private PeerCreatorFactory creatorFactory;

    @Autowired
    private StorageCreatorComponent storages;

    @Autowired
    private Miner miner;

    private final ConnectionServices services;
    @GetMapping("/account")
    @CrossOrigin
    public ResponseEntity<MockAccount> getChain() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {

        MockAccount account = GlacierUtils.generateMockAccount();

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(account, headers, HttpStatus.OK);
    }

    @GetMapping("/transaction/{hash}")
    public ResponseEntity<TransactionBaseModel> getTransaction(@PathVariable(value = "hash") String txHash) throws RocksDBException {
        TransactionBaseModel transaction = new TransactionBaseModel();

        if(txHash != null) {
            byte[] txKey = txHash.getBytes();

            BaseEmptyModel serializedTx = this.storages.tx().get(txKey);

            if(serializedTx != null) {
                transaction = (TransactionBaseModel) serializedTx;
            }
        }

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(transaction, headers, HttpStatus.OK);
    }

    @GetMapping("/block/{id}")
    public ResponseEntity<BaseBlockModel> getTransaction(@PathVariable(value = "id") Integer blockId) throws RocksDBException {
        BaseBlockModel block = new BaseBlockModel();

        if(blockId != null) {
            byte[] txKey = blockId.toString().getBytes();

            BaseEmptyModel serializedTx = this.storages.blocks().get(txKey);

            if(serializedTx != null) {
                block = (BaseBlockModel) serializedTx;
            }
        }

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(block, headers, HttpStatus.OK);
    }

    @GetMapping("/blockchain")
    public ResponseEntity<List<Integer>> getBlocksIds() throws RocksDBException {
        List<Integer> ids = new ArrayList<>();

        RocksIterator iterator = this.storages.blocks().getRaw().newIterator();

        for(iterator.seekToFirst(); iterator.isValid(); iterator.next()){
            final Integer blockId = (Integer) SerializationUtils.deserialize(iterator.key());
            ids.add(blockId);
        }

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(ids, headers, HttpStatus.OK);
    }

    @PostMapping("/mining")
    public void mining(@RequestBody AccountEntry privateKey) throws NoSuchAlgorithmException, RocksDBException {
        //Start and stop the miner



        if(miner.getFlag()){
            miner.stop();
        } else {
            miner.start(privateKey.getAddress());
        }
    }



        //"address": "1Lxu7ASy3QhmEPPkLaBSmSx2VkBNzTbkur",
        //"wif": "5KJ9nn4szTnx128dezDkxDMjJKG5cAtPiEVQ46rUM3CceNGy5gP",
        //"privateKey": "c2f7ef621287cecb7a42df351e7075cad969ffcb2d6557424214f8ea7309e38b"



}
