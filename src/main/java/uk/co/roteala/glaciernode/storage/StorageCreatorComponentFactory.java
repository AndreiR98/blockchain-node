package uk.co.roteala.glaciernode.storage;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.DbPath;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import uk.co.roteala.glaciernode.configs.GlacierConfigs;
import uk.co.roteala.storage.StorageType;
import uk.co.roteala.utils.GlacierUtils;

import java.nio.file.Path;
import java.util.List;

@Lazy
@Slf4j
@Component
public class StorageCreatorComponentFactory{

    private GlacierConfigs configs = new GlacierConfigs();

    private RocksDB peers;
    private RocksDB mempool;
    private RocksDB blocks;
    private RocksDB wallet;
    private RocksDB tx;

    public StorageCreatorComponentFactory() throws RocksDBException {
        this.peers = initPeersStorage();
        this.mempool = initMempoolStorage();
        this.blocks = initBocksStorage();
        this.wallet = initWalletStorage();
        this.tx = initTransactionStorage();
    }

    public RocksDB getStorage(StorageType type) {
        RocksDB db = null;

        switch (type) {
            case TX:
                db = this.tx;
                break;
            case PEERS:
                db = this.peers;
                break;
            case BLOCKS:
                db = this.blocks;
                break;
            case WALLET:
                db = this.wallet;
                break;
            case MEMPOOL:
                db = this.mempool;
                break;
        }

        return db;
    }

    private RocksDB initPeersStorage() throws RocksDBException {
        try{
            Options options = new Options();
            options.setCreateIfMissing(true);

            String path = null;

            //Implement system check
            if(GlacierUtils.getSystem()){
                //use windows path
                path = configs.getRootWindows()+configs.getPeersPath();
            } else {
                path = configs.getRootLinux()+configs.getPeersPath();
            }
            final DbPath pathDb = new DbPath(Path.of(path), 1L);

            options.setDbLogDir(path+"/logs");
            options.setDbPaths(List.of(pathDb));

            log.info("Open storage at:{}",path);

            return RocksDB.open(options, path);
        }catch (Exception e) {
            log.error("Unable to open sotrage at:");
            throw new RocksDBException("");
        }
    }

    private RocksDB initMempoolStorage() throws RocksDBException {
        try{
            Options options = new Options();
            options.setCreateIfMissing(true);

            String path = null;

            //Implement system check
            if(GlacierUtils.getSystem()){
                //use windows path
                path = configs.getRootWindows()+configs.getMemepoolPath();
            } else {
                path = configs.getRootLinux()+configs.getMemepoolPath();
            }
            final DbPath pathDb = new DbPath(Path.of(path), 1L);

            options.setDbLogDir(path+"/logs");
            options.setDbPaths(List.of(pathDb));

            return RocksDB.open(options, path);
        }catch (Exception e) {
            log.error("Unable to open sotrage at:");
            throw new RocksDBException("");
        }
    }

    private RocksDB initBocksStorage() throws RocksDBException {
        try{
            Options options = new Options();
            options.setCreateIfMissing(true);

            String path = null;

            //Implement system check
            if(GlacierUtils.getSystem()){
                //use windows path
                path = configs.getRootWindows()+configs.getStoragePath();
            } else {
                path = configs.getRootLinux()+configs.getStoragePath();
            }
            final DbPath pathDb = new DbPath(Path.of(path), 1L);

            options.setDbLogDir(path+"/logs");
            options.setDbPaths(List.of(pathDb));

            return RocksDB.open(options, path);
        }catch (Exception e) {
            log.error("Unable to open sotrage at:");
            throw new RocksDBException("");
        }
    }

    private RocksDB initWalletStorage() throws RocksDBException {
        try{
            Options options = new Options();
            options.setCreateIfMissing(true);

            String path = null;

            //Implement system check
            if(GlacierUtils.getSystem()){
                //use windows path
                path = configs.getRootWindows()+configs.getWalletPath();
            } else {
                path = configs.getRootLinux()+configs.getWalletPath();
            }
            final DbPath pathDb = new DbPath(Path.of(path), 1L);

            options.setDbLogDir(path+"/logs");
            options.setDbPaths(List.of(pathDb));

            return RocksDB.open(options, path);
        }catch (Exception e) {
            log.error("Unable to open sotrage at:");
            throw new RocksDBException("");
        }
    }

    private RocksDB initTransactionStorage() throws RocksDBException {
        try{
            Options options = new Options();
            options.setCreateIfMissing(true);

            String path = null;

            //Implement system check
            if(GlacierUtils.getSystem()){
                //use windows path
                path = configs.getRootWindows()+configs.getTxPath();
            } else {
                path = configs.getRootLinux()+configs.getTxPath();
            }
            final DbPath pathDb = new DbPath(Path.of(path), 1L);

            options.setDbLogDir(path+"/logs");
            options.setDbPaths(List.of(pathDb));

            return RocksDB.open(options, path);
        }catch (Exception e) {
            log.error("Unable to open sotrage at:");
            throw new RocksDBException("");
        }
    }
}
