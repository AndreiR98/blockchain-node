package uk.co.roteala.glaciernode.storage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

@Data
@RequiredArgsConstructor
public class StorageData implements StorageInterface {
    private final StorageHandlers storageData;

    private final RocksDB storagePeers;

    private final StorageHandlers storageMempool;

    private final RocksDB stateTrie;

    @Override
    public StorageHandlers getStorageData() {
        return storageData;
    }

    @Override
    public RocksDB getPeers() {
        return storagePeers;
    }

    @Override
    public StorageHandlers getMempool(){
        return storageMempool;
    }

    @Override
    public RocksDB getStateTrie() {
        return stateTrie;
    }
}
