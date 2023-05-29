package uk.co.roteala.glaciernode.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.SerializationUtils;
import uk.co.roteala.common.BaseBlockModel;
import uk.co.roteala.common.BaseEmptyModel;
import uk.co.roteala.common.TransactionBaseModel;
import uk.co.roteala.net.Peer;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServices {
    @Autowired
    private StorageInterface storages;

    public void addBlock(String key, BaseEmptyModel data) throws RocksDBException {
        final byte[] serializedData = SerializationUtils.serialize(data);
        final byte[] serializedKey = key.getBytes();

        RocksDB storage = storages.getStorageData();

        try {
            final ColumnFamilyHandle blockCF = storage
                    .createColumnFamily(new ColumnFamilyDescriptor("blocks".getBytes()));

            storage.put(blockCF, serializedKey, serializedData);
            storage.flush(new FlushOptions().setWaitForFlush(true));
        }catch (Exception e){
            new Exception("Storage exception, while adding blocks" + e);
        }
    }

    public BaseBlockModel getBlockByKey(String key) throws RocksDBException {
        final byte[] serializedKey;

        BaseBlockModel block = null;

        RocksDB storage = storages.getStorageData();

        if(key != null) {
            serializedKey = key.getBytes();

            try {
                final ColumnFamilyHandle blockCF = storage
                        .createColumnFamily(new ColumnFamilyDescriptor("blocks".getBytes()));

                block = (BaseBlockModel) SerializationUtils.deserialize(storage.get(blockCF, serializedKey));

                if(block == null) {
                    log.error("Failed to retrieve block with index:{}", key);
                    new Exception("Failed to retrieve block");
                }
            } catch (Exception e){
                new Exception("Storage failed to retrieve block:"+ e);
            }
        }

        return block;
    }

    public void addTransaction(String key, TransactionBaseModel data) throws RocksDBException {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedData = SerializationUtils.serialize(data);

        RocksDB storage = storages.getStorageData();

        try {
            final ColumnFamilyHandle transactionCF = storage
                    .createColumnFamily(new ColumnFamilyDescriptor("transactions".getBytes()));

            storage.put(transactionCF, serializedKey, serializedData);
            storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e) {
            new Exception("Storage exception, while adding new transactions" + e);
        }
    }

    public TransactionBaseModel getTransactionByKey(String key) throws RocksDBException {
        final byte[] serializedKey;

        TransactionBaseModel transaction = null;

        RocksDB storage = storages.getStorageData();

        if(key != null) {
            serializedKey = key.getBytes();

            try {
                final ColumnFamilyHandle transactionCF = storage
                        .createColumnFamily(new ColumnFamilyDescriptor("transactions".getBytes()));

                transaction = (TransactionBaseModel) SerializationUtils.deserialize(storage.get(transactionCF, serializedKey));

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
}
