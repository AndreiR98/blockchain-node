package uk.co.roteala.glaciernode.storage;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.co.roteala.storage.StorageComponent;
import uk.co.roteala.storage.StorageType;
import uk.co.roteala.storage.StoragesCreatorComponent;

@Slf4j
@Service
@Configuration
@SuppressWarnings({"rawtypes", "unchecked"})
public class StorageCreatorComponent implements StoragesCreatorComponent {

    private StorageCreatorComponentFactory storageCreatorComponent;

    @Autowired
    public void StorageCreatorComponentFactory(StorageCreatorComponentFactory storageCreatorComponent) {
        this.storageCreatorComponent = storageCreatorComponent;
    }

    @Override
    public StorageComponent mempool() {
        return new StorageComponent(storageCreatorComponent.getStorage(StorageType.MEMPOOL));
    }

    @Override
    public StorageComponent wallet() {
        return new StorageComponent(storageCreatorComponent.getStorage(StorageType.WALLET));
    }

    @Override
    public StorageComponent tx() throws RocksDBException {
        return new StorageComponent(storageCreatorComponent.getStorage(StorageType.TX));
    }

    @Override
    public StorageComponent blocks() throws RocksDBException {
        return new StorageComponent(storageCreatorComponent.getStorage(StorageType.BLOCKS));
    }

    @Override
    public StorageComponent peers() throws RocksDBException {
        return new StorageComponent(storageCreatorComponent.getStorage(StorageType.PEERS));
    }

    @Override
    public StorageComponent chainState() throws RocksDBException {
        return new StorageComponent(storageCreatorComponent.getStorage(StorageType.CHAIN));
    }

}
