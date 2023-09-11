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
import uk.co.roteala.exceptions.TransactionException;
import uk.co.roteala.exceptions.errorcodes.StorageErrorCode;
import uk.co.roteala.exceptions.errorcodes.TransactionErrorCode;
import uk.co.roteala.net.Peer;
import uk.co.roteala.utils.BlockchainUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StorageServices {
    @Autowired
    private StorageInterface storages;

    public void addTransaction(String key, Transaction data) {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedData = SerializationUtils.serialize(data);

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getStorageData();

        try {
            storage.getDatabase().put(storage.getHandlers().get(1), serializedKey, serializedData);
            storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true), storage.getHandlers().get(1));
        } catch (Exception e) {
            throw new StorageException(StorageErrorCode.STORAGE_FAILED);
        }
    }

    public List<String> groupSyncTransaction(int blockIndex) {
        List<String> transactionsList = new ArrayList<>();
        try {
            RocksDB.loadLibrary();

            StorageHandlers handlers = storages.getStorageData();

            RocksIterator iterator = handlers.getDatabase()
                    .newIterator(handlers.getHandlers().get(1));

            List<Transaction> transactionsModelList = new ArrayList<>();

            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                Transaction transaction = SerializationUtils.deserialize(iterator.value());

                if(transaction.getBlockNumber() == blockIndex) {
                    transactionsModelList.add(transaction);
                }
            }

            transactionsModelList.sort(Comparator.comparingLong(Transaction::getTransactionIndex));

        } catch (Exception e) {
            log.error("Failed to group transactions:{}", e.getMessage());
        }

        return transactionsList;
    }

    public Transaction getTransactionByKey(String key) {
        final byte[] serializedKey = key.getBytes(StandardCharsets.UTF_8);

        RocksDB.loadLibrary();

        Transaction transaction = null;

        StorageHandlers storage = storages.getStorageData();

        try {
            if(storage.getDatabase().get(storage.getHandlers().get(1), serializedKey) != null) {
                transaction = (Transaction) SerializationUtils.deserialize(
                        storage.getDatabase().get(storage.getHandlers().get(1), serializedKey));
            }
        } catch (Exception e){
            log.error("Could not find transactions:{}", key);
        }

        return transaction;
    }

    public void addBlock(String key, Block block){
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedData = SerializationUtils.serialize(block);

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getStorageData();

        try {
            storage.getDatabase().put(storage.getHandlers().get(2), serializedKey, serializedData);
            storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true), storage.getHandlers().get(2));
        } catch (Exception e) {
            throw new StorageException(StorageErrorCode.BLOCK_NOT_FOUND);
        }
    }

    public void addBlockMempool(String key, Block block) {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedData = SerializationUtils.serialize(block);

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getMempool();

        try {
            storage.getDatabase().put(storage.getHandlers().get(2), serializedKey, serializedData);
            storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true),
                    storage.getHandlers().get(2));
        } catch (Exception e) {
            throw new StorageException(StorageErrorCode.STORAGE_FAILED);
        }
    }

    public Block getPseudoBlockByHash(String key) {
        final byte[] serializedKey;

        RocksDB.loadLibrary();

        Block block = null;

        StorageHandlers storage = storages.getMempool();

        if(key != null) {
            serializedKey = key.getBytes();

            try {
                if(storage.getDatabase().get(storage.getHandlers().get(2), serializedKey) == null) {
                    throw new StorageException(StorageErrorCode.BLOCK_NOT_FOUND);
                }

                block = (Block) SerializationUtils.deserialize(
                        storage.getDatabase().get(storage.getHandlers().get(2), serializedKey));

            } catch (Exception e){
                log.error("Error:{}", e.getMessage());
            }
        }

        return block;
    }

    public void addMempool(String key, PseudoTransaction transaction) {
        final byte[] serializedKey = key.getBytes();
        final byte[] serializedTransaction = SerializationUtils.serialize(transaction);

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getMempool();

        try {
            if(serializedKey == null) {
                log.error("Failed to add, key:{}", key);
                throw new StorageException(StorageErrorCode.SERIALIZATION);
            }

            if(serializedTransaction == null) {
                log.error("Failed to add, transaction:{}", transaction);
                throw new StorageException(StorageErrorCode.SERIALIZATION);
            }

            storage.getDatabase().put(storage.getHandlers().get(1),
                    serializedKey, serializedTransaction);
            storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true), storage.getHandlers().get(1));
        } catch (Exception e) {
            log.error("Failed to store memepool transaction"+ transaction);
            throw new StorageException(StorageErrorCode.MEMPOOL_FAILED);
        }
    }

    public PseudoTransaction getMempoolTransactionByKey(String key) {
        final byte[] serializedKey;

        RocksDB.loadLibrary();

        PseudoTransaction transaction = null;

        StorageHandlers storage = storages.getMempool();

        if(key != null) {
            serializedKey = key.getBytes();

            try {
                byte[] serializedTransaction = storage.getDatabase().get(storage.getHandlers().get(1), serializedKey);

                if(serializedTransaction != null) {
                    //throw new TransactionException(StorageErrorCode.TRANSACTION_NOT_FOUND);
                    transaction = (PseudoTransaction) SerializationUtils.deserialize(serializedTransaction);
                }

            } catch (Exception e){
                log.info("Error:{}", e.getMessage());
            }
        }

        return transaction;
    }

    public void deleteMempoolTransaction(String key) {
        final byte[] serializedKey = key.getBytes();

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getMempool();

        try {
            storage.getDatabase()
                    .delete(storage.getHandlers().get(1), serializedKey);
        } catch (Exception e) {
            throw new TransactionException(TransactionErrorCode.TRANSACTION_NOT_FOUND);
        }
    }

    public void deleteMempoolBlock(String hash) {
        final byte[] key = hash.getBytes();

        RocksDB.loadLibrary();

        StorageHandlers storage = storages.getMempool();

        try {
            storage.getDatabase()
                    .delete(storage.getHandlers().get(2), key);
        } catch (Exception e) {
            log.error("Error while deleting block:{}", e.getMessage());
        }
    }

    public List<Block> getPseudoBlocks() {
        List<Block> pseudoBlocks = new ArrayList<>();

        try {
            StorageHandlers handlers = storages.getMempool();

            RocksIterator iterator = handlers.getDatabase()
                    .newIterator(handlers.getHandlers().get(2));

            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                Block block = SerializationUtils.deserialize(iterator.value());

                if(block != null) {
                    pseudoBlocks.add(block);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching block from store");
        }

        return pseudoBlocks;
    }

    public void deleteMempoolBlocksAtIndex(Integer index) {
        RocksDB.loadLibrary();

        List<Block> pseudoBlocks = getPseudoBlocks();

        StorageHandlers storage = storages.getMempool();

        try {
            List<Block> filteredBlocks = pseudoBlocks.stream()
                    .filter(block -> block.getHeader().getIndex().equals(index))
                    .collect(Collectors.toList());

            for (Block filteredBlock : filteredBlocks) {
                final byte[] serializedKey = filteredBlock.getHash().getBytes();
                storage.getDatabase().delete(storage.getHandlers().get(2), serializedKey);
                storage.getDatabase().flush(new FlushOptions().setWaitForFlush(true), storage.getHandlers().get(2));
            }
        } catch (Exception e) {
           log.error("Faield to delete mempool blocks!");
        }
    }

    /**
     * */
    public List<String> retrieveTransactionForGroup(Integer index) {
        List<String> transactions = new ArrayList<>();

        try {
            RocksDB.loadLibrary();

            StorageHandlers handlers = storages.getStorageData();

            RocksIterator iterator = handlers.getDatabase()
                    .newIterator(handlers.getHandlers().get(1));

            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                Transaction transaction = SerializationUtils.deserialize(iterator.value());

                if(Objects.equals(transaction.getBlockNumber(), index)) {
                    transactions.add(transaction.getHash());
                }
            }
        } catch (Exception e) {
            log.error("Error while retrieve transactions per block:{}", e.getMessage());
        }

        return transactions;
    }

    /**
     * Group a bundle of psuedo transaction that falls under the block time
     * Order them by blockTime(timeWindow)
     * Then order them by values
     * IF fees > greater than 1.5% of value, then take in consideration
     *
     *
     *
     * @param timeWindow
     * @return List
     * */
    public List<PseudoTransaction> getPseudoTransactionGrouped(long timeWindow) {
        List<PseudoTransaction> returnTransactions = new ArrayList<>();

        RocksDB.loadLibrary();

        try {
            StorageHandlers handlers = storages.getMempool();

            List<PseudoTransaction> withPriority = new ArrayList<>();
            List<PseudoTransaction> withoutPriority = new ArrayList<>();

            final int maxSize = 1024 * 124;


            RocksIterator iterator = handlers.getDatabase()
                    .newIterator(handlers.getHandlers().get(1));

            for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                PseudoTransaction transaction = SerializationUtils.deserialize(iterator.value());

                if((transaction != null)
                        && transaction.getStatus() != TransactionStatus.PROCESSED
                        && transaction.getStatus() != TransactionStatus.SUCCESS
                        //&& transaction.getStatus() != TransactionStatus.LOCKED
                        && transaction.getTimeStamp() <= timeWindow){

                    BigDecimal feesPercentage = transaction.getFees().getFees().getValue()
                            .divide(transaction.getValue().getValue(), 6, RoundingMode.HALF_UP);

                    if(feesPercentage.compareTo(new BigDecimal("1.55")) > 0) {
                        withPriority.add(transaction);
                    } else {
                        withoutPriority.add(transaction);
                    }
                }
            }

            //Order the non-priority by time
            withoutPriority.sort(Comparator.comparingLong(PseudoTransaction::getTimeStamp).reversed());
            withPriority.sort(Comparator.comparing(t -> t.getFees().getFees().getValue()));//Compare them by fees in ascending order


            //Add all priority transactions first
            returnTransactions.addAll(withPriority);

            int transactionBytesSize = SerializationUtils.serialize((Serializable) returnTransactions).length;

            if(!withoutPriority.isEmpty()) {
                int index = 0;
                while ((transactionBytesSize < maxSize) || index < withoutPriority.size()){
                    PseudoTransaction transaction = withoutPriority.get(index);

                    returnTransactions.add(transaction);

                    // Update transactionBytesSize with the size of the newly added transaction
                    transactionBytesSize += SerializationUtils.serialize((Serializable) transaction).length;

                    if((index+1) >= withoutPriority.size()) {
                        break;
                    }

                    index++;
                }
            }
        } catch (Exception e) {
            log.info("Error:{}", e.getMessage());
        }

        //Return 1MB list of transactions
        return returnTransactions;
    }

    public Block getBlockByIndex(Integer index) {
        final byte[] serializedKey;

        RocksDB.loadLibrary();

        Block block = null;

        StorageHandlers storage = storages.getStorageData();

        if(index != null) {
            serializedKey = index.toString().getBytes();

            try {
                if(storage.getDatabase().get(storage.getHandlers().get(2), serializedKey) != null) {
                    block = SerializationUtils.deserialize(storage.getDatabase().get(storage.getHandlers().get(2), serializedKey));
                }
            } catch (Exception e){
                log.error("Block not found!");
            }
        }

        return block;
    }

    public void addPeer(Peer peer) {
        final byte[] serializedKey = (peer.getAddress()).getBytes();
        final byte[] serializedPeer = SerializationUtils.serialize(peer);

        try {
            RocksDB storage = storages.getPeers();

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

    public List<Peer> getPeersFromStorage(){
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
            throw new StorageException(StorageErrorCode.PEER_NOT_FOUND);
        }

        return peers;
    }

    public ChainState getStateTrie() {
        final byte[] key = "stateChain".getBytes();

        ChainState state;

        RocksDB storage = storages.getStateTrie();

        try {
            if(storage.get(key) == null) {
                state = null;
            } else {
                state = (ChainState) SerializationUtils.deserialize(storage.get(key));
            }
        } catch (Exception e){
            state = null;
            throw new StorageException(StorageErrorCode.STORAGE_FAILED, "stateChain");
        }

        return state;
    }

    public NodeState getNodeState() {
        final byte[] key = "nodeState".getBytes();

        NodeState state = null;

        RocksDB storage = storages.getStateTrie();

        try {
            if(storage.get(key) == null) {
                throw new StorageException(StorageErrorCode.STATE_NOT_FOUND);
            }

            state = (NodeState) SerializationUtils.deserialize(storage.get(key));

        } catch (StorageException | RocksDBException e){
            log.error("Nodestate error:{}", e.getMessage());
        }

        return state;
    }

    public void addStateTrie(ChainState state) {
        final byte[] key = "stateChain".getBytes();

        RocksDB storage = storages.getStateTrie();

        try {
                storage.put(key, SerializationUtils.serialize(state));
                storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e){
            throw new StorageException(StorageErrorCode.STORAGE_FAILED);
        }
    }

    public void addNodeState(NodeState state) {
        final byte[] key = "nodeState".getBytes();

        RocksDB storage = storages.getStateTrie();

        try {
                storage.put(key, SerializationUtils.serialize(state));
                storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e){
            throw new StorageException(StorageErrorCode.STORAGE_FAILED);
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
                account = SerializationUtils.deserialize(storage.get(address.getBytes()));
            }
        } catch (StorageException | RocksDBException e){
            throw new StorageException(StorageErrorCode.ACCOUNT_NOT_FOUND);
        }

        return account;
    }

    //public List<AccountModel> getAllAccounts() {}

    public void updateStateTrie(ChainState newState) {
        final byte[] key = "stateChain".getBytes();

        RocksDB storage = storages.getStateTrie();

        try {
            if(newState == null) {
                throw new StorageException(StorageErrorCode.STATE_NOT_FOUND);
            }

            storage.put("stateChain".getBytes(), SerializationUtils.serialize(newState));
            storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e){
            log.info("Error:{}", e.getMessage());
        }
    }

    public void updateNodeState(NodeState newNodeState) {
        RocksDB storage = storages.getStateTrie();

        try {
            if(newNodeState == null) {
                throw new StorageException(StorageErrorCode.STATE_NOT_FOUND);
            }

            storage.put("nodeState".getBytes(), SerializationUtils.serialize(newNodeState));
            storage.flush(new FlushOptions().setWaitForFlush(true));
        } catch (Exception e){
            log.info("Error:{}", e.getMessage());
        }
    }
}
