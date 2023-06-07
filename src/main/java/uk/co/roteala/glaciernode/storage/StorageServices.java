package uk.co.roteala.glaciernode.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.util.SerializationUtils;
import uk.co.roteala.common.BaseBlockModel;
import uk.co.roteala.common.BaseEmptyModel;
import uk.co.roteala.common.TransactionBaseModel;
import uk.co.roteala.common.UTXO;
import uk.co.roteala.net.Peer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class StorageServices {
    @Autowired
    private StorageInterface storages;

    public void addTransaction(String key, TransactionBaseModel data) throws Exception {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedData = SerializationUtils.serialize(data);

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getStorageData();

        try {
            storage.getDatabase().put(storage.getHandlers().get(1), serializedKey, serializedData);
            storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e) {
            throw new Exception("Storage exception, while adding new transactions" + e);
        }
    }

    public TransactionBaseModel getTransactionByKey(String key) throws RocksDBException {
        final byte[] serializedKey;

        RocksDB.loadLibrary();

        TransactionBaseModel transaction = null;

        StorageHandlers storage = storages.getStorageData();

        if(key != null) {
            serializedKey = key.getBytes();

            try {
                transaction = (TransactionBaseModel) SerializationUtils.deserialize(
                        storage.getDatabase().get(storage.getHandlers().get(1), serializedKey));

                if(transaction == null) {
                    log.error("Failed to retrieve transaction with hash:{}", key);
                    new Exception("Failed to retrieve transaction");
                }
            } catch (Exception e){
                new Exception("Storage failed to retrieve transaction:"+ e);
            }
        }

        return transaction;
    }

    public void addBlock(String key, BaseBlockModel block) throws Exception {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedData = SerializationUtils.serialize(block);

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getStorageData();

        try {
            storage.getDatabase().put(storage.getHandlers().get(2), serializedKey, serializedData);
            storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e) {
            throw new Exception("Storage exception, while adding new transactions" + e);
        }
    }

    public void addMempool(String key, TransactionBaseModel transaction) throws RocksDBException {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedTransaction = SerializationUtils.serialize(transaction);

        RocksDB storage = storages.getMempool();

        try {
            if(serializedKey == null) {
                log.error("Failed to add, key:{}", key);
            }

            if(serializedTransaction == null) {
                log.error("Failed to add, transaction:{}", transaction);
                new Exception("Failed due to transaction issue");
            }

            storage.put(serializedKey, serializedTransaction);
        } catch (Exception e) {
            log.error("Failed to store memepool transaction"+ transaction);
        }
    }

    public TransactionBaseModel getMempoolTransactionByKey(String key) throws RocksDBException {
        final byte[] serializedKey;

        TransactionBaseModel transaction = null;

        RocksDB storage = storages.getMempool();

        if(key != null) {
            serializedKey = key.getBytes();

            try {
                transaction = (TransactionBaseModel) SerializationUtils.deserialize(storage.get(serializedKey));

                if(transaction == null) {
                    log.error("Failed to retrieve transaction with hash:{}", key);
                    new Exception("Failed to retrieve transaction");
                }
            } catch (Exception e){
                new Exception("Storage failed to retrieve transaction:"+ e);
            }
        }

        return transaction;
    }

    public void addPeer(Peer peer) {
        final byte[] serializedKey = (peer.getAddress() + peer.getPort()).getBytes();
        final byte[] serializedPeer = SerializationUtils.serialize(peer);

        try {
            RocksDB storage = storages.getPeers();

            if(serializedKey == null) {
                log.error("Failed to add, key:{}", peer.getAddress());
            }

            if(serializedPeer == null) {
                log.error("Failed to add, peer:{}", peer);
                new RocksDBException("Failed due to peer issue");
            }

            storage.put(serializedKey, serializedPeer);
            storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (RocksDBException e) {
            log.error("Failed to store peer"+ serializedPeer + e);
            //throw new RocksDBException("Error");
        }
    }

    public void updatePeerStatus(Peer peer, boolean status){
        peer.setActive(status);

        addPeer(peer);
        log.info("Peer status updated successfully!");
    }

    public List<Peer> getPeersFromStorage() throws RocksDBException {
        List<Peer> peers = new ArrayList<>();

        RocksDB storage = storages.getPeers();

        try{
            RocksIterator iterator = storage.newIterator();

            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                Peer deserializedPeer = (Peer) SerializationUtils.deserialize(iterator.value());

                if(deserializedPeer.isActive()) {
                    peers.add(deserializedPeer);
                }
            }
        } catch (Exception e) {
            log.error("Failed to retrieve peers");
            new Exception("Storage failed to retrieve peers!");
        }

        return peers;
    }

//    public void flush() {
//        try{
//            storages.getStorageData().flush(new FlushOptions().setWaitForFlush(true));
//        } catch (Exception e) {
//            log.info("Failed to flush!");
//        }
//
//    }

    public List<String> getAddressTransactions(String address) {
        List<String> transactionByAddress = new ArrayList<>();

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getStorageData();

        if (address != null) {
            try (final RocksIterator iterator = storage.getDatabase()
                    .newIterator(storage.getHandlers().get(1))) {

                iterator.seekToFirst();

                while (iterator.isValid()) {
                    byte[] valueBytes = iterator.value();
                    TransactionBaseModel transaction = (TransactionBaseModel) SerializationUtils.deserialize(valueBytes);

                    if (transaction != null && Objects.equals(transaction.getTo(), address)) {
                        transactionByAddress.add(transaction.getHash());
                    }

                    iterator.next();
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to retrieve transactions from storage", e);
            }
        }

        return transactionByAddress;
    }
}
