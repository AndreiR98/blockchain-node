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

    private final RocksDB storageMempool;

    @Override
    public StorageHandlers getStorageData() {
        return storageData;
    }

    @Override
    public RocksDB getPeers() throws RocksDBException {
        return storagePeers;
    }

    @Override
    public RocksDB getMempool() throws RocksDBException {
        return storageMempool;
    }
}
