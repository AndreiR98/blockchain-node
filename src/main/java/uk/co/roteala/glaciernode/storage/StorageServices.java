package uk.co.roteala.glaciernode.storage;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.rocksdb.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.co.roteala.common.*;
import uk.co.roteala.common.Transaction;
import uk.co.roteala.common.events.Message;
import uk.co.roteala.exceptions.StorageException;
import uk.co.roteala.exceptions.errorcodes.StorageErrorCode;
import uk.co.roteala.net.Peer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class StorageServices {
    @Autowired
    private StorageInterface storages;

    public void addTransaction(String key, Transaction data) throws Exception {
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

    public Transaction getTransactionByKey(String key) throws RocksDBException {
        final byte[] serializedKey;

        RocksDB.loadLibrary();

        Transaction transaction = null;

        StorageHandlers storage = storages.getStorageData();

        if(key != null) {
            serializedKey = key.getBytes();

            try {
                transaction = (Transaction) SerializationUtils.deserialize(
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

    public void addBlock(String key, Block block) throws Exception {
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

    public void addMempool(String key, PseudoTransaction transaction) {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedTransaction = SerializationUtils.serialize(transaction);

        RocksDB storage = storages.getMempool();

        try {
            if(serializedKey == null) {
                log.error("Failed to add, key:{}", key);
                throw new StorageException(StorageErrorCode.SERIALIZATION);
            }

            if(serializedTransaction == null) {
                log.error("Failed to add, transaction:{}", transaction);
                throw new StorageException(StorageErrorCode.SERIALIZATION);
            }

            storage.put(new WriteOptions().setSync(true), serializedKey, serializedTransaction);
        } catch (Exception e) {
            log.error("Failed to store memepool transaction"+ transaction);
            throw new StorageException(StorageErrorCode.MEMPOOL_FAILED);
        }
    }

    public PseudoTransaction getMempoolTransactionByKey(String key) {
        final byte[] serializedKey;

        PseudoTransaction transaction = null;

        RocksDB storage = storages.getMempool();

        if(key != null) {
            serializedKey = key.getBytes();

            try {
                transaction = (PseudoTransaction) SerializationUtils.deserialize(storage.get(serializedKey));

                if(transaction == null) {
                    log.error("Failed to retrieve transaction with hash:{}", key);
                    throw new StorageException(StorageErrorCode.MEMPOOL_NOT_FOUND);
                }
            } catch (StorageException | RocksDBException e){
                return null;
            }
        }

        return transaction;
    }

    //public boolean hasPseudoTransactions() {}

    //public List<String> getPseudoTransactions() {}

    public Block getBlockByIndex(Integer index) {
        final byte[] serializedKey;

        RocksDB.loadLibrary();

        Block block = null;

        StorageHandlers storage = storages.getStorageData();

        if(index != null) {
            serializedKey = index.toString().getBytes();

            try {
                block = (Block) SerializationUtils.deserialize(
                        storage.getDatabase().get(storage.getHandlers().get(0), serializedKey));

                if(block == null) {
                    log.error("Failed to retrieve block:{}", index);
                    new Exception("Failed to retrieve transaction");
                }
            } catch (Exception e){
                new Exception("Storage failed:"+ e);
            }
        }

        return block;
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

    public ChainState getStateTrie() {
        final byte[] key = "stateChain".getBytes();

        ChainState state = null;

        RocksDB storage = storages.getStateTrie();

        try {
            state = (ChainState) SerializationUtils.deserialize(storage.get(key));

            if(state == null) {
                throw new StorageException(StorageErrorCode.STATE_NOT_FOUND);
            }
        } catch (StorageException | RocksDBException e){
            throw new StorageException(StorageErrorCode.STORAGE_FAILED, "stateChain");
        }

        return state;
    }

    public void updateStateTrie(ChainState newState) throws RocksDBException {
        final byte[] key = "stateChain".getBytes();

        ChainState state = null;

        RocksDB storage = storages.getStateTrie();

        try {
            state = (ChainState) SerializationUtils.deserialize(storage.get(key));

            if(state == null) {
                new Exception("Failed to retrieve state chain!");
            }

            state.setTarget(newState.getTarget());
            state.setLastBlockIndex(newState.getLastBlockIndex());
            state.setAccounts(state.getAccounts());

            storage.put(key, SerializationUtils.serialize(state));
        } catch (Exception e){
            new Exception("Storage failed to retrieve state chain:"+ e);
        }
    }

    public void addStateTrie(ChainState newState) throws RocksDBException {
        final byte[] key = "stateChain".getBytes();

        ChainState state = null;

        RocksDB storage = storages.getStateTrie();

        try {
            state = (ChainState) SerializationUtils.deserialize(storage.get(key));

            if(state == null) {
                new Exception("Failed to retrieve state chain!");
            }

            state.setTarget(newState.getTarget());
            state.setLastBlockIndex(newState.getLastBlockIndex());
            state.setAccounts(newState.getAccounts());

            storage.put(key, SerializationUtils.serialize(state));
        } catch (Exception e){
            new Exception("Storage failed to retrieve state chain:"+ e);
        }
    }

    public void updateAccount(AccountModel account) {
        RocksDB storage = storages.getStateTrie();

        try {
            storage.put(new WriteOptions().setSync(true), account.getAddress().getBytes(), SerializationUtils.serialize(account));
            storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e) {
            throw new StorageException(StorageErrorCode.ACCOUNT_NOT_FOUND);
        }

    }

    /**
     * is needCreate is true, create the account
     * */
    public AccountModel getAccountByAddress(String address) {
        RocksDB storage = storages.getStateTrie();

        AccountModel account = new AccountModel();

        try {
            if(storage.get(address.getBytes()) == null) {
                account = account.empty(address);
            } else {
                account = org.apache.commons.lang3.SerializationUtils.deserialize(storage.get(address.getBytes()));
            }
        } catch (StorageException | RocksDBException e){
            throw new StorageException(StorageErrorCode.ACCOUNT_NOT_FOUND);
        }

        return account;
    }

    //public List<AccountModel> getAllAccounts() {}

    public void deleteData(Message message) {}
}
