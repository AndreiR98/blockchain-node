package uk.co.roteala.glaciernode.storage;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import uk.co.roteala.net.Peer;

public interface StorageInterface {
    StorageHandlers getStorageData();

    RocksDB getPeers() throws RocksDBException;

    RocksDB getMempool();

    RocksDB getStateTrie();
}
