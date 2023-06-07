package uk.co.roteala.glaciernode.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@AllArgsConstructor
public class StorageHandlers {

    @Getter
    private RocksDB database;
    @Getter
    private List<ColumnFamilyHandle> handlers;

//    public StorageHandlers(RocksDB database, List<ColumnFamilyHandle> handlers) {
//        this.database = database;
//        this.handlers = handlers;
//    }
}
