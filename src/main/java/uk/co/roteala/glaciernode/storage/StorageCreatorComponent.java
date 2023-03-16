package uk.co.roteala.glaciernode.storage;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.co.roteala.storage.StorageComponent;
import uk.co.roteala.storage.StorageType;

@Slf4j
@Service
@SuppressWarnings({"rawtypes", "unchecked"})
public class StorageCreatorComponent implements uk.co.roteala.storage.StorageCreatorComponent {

    private StorageCreatorComponentFactory storageCreatorComponent;

    @Autowired
    public void StorageCreatorComponentFactory(StorageCreatorComponentFactory storageCreatorComponent) {
        this.storageCreatorComponent = storageCreatorComponent;
    }

    @Override
    public StorageComponent mempool() throws RocksDBException {
        return new StorageComponent(storageCreatorComponent.getStorage(StorageType.MEMPOOL));
    }

    @Override
    public StorageComponent wallet() throws RocksDBException {
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

}
