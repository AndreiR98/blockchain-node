package uk.co.roteala.glaciernode.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import uk.co.roteala.glaciernode.configs.GlacierConfigs;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Storage {
    private GlacierConfigs configs = new GlacierConfigs();

    @Bean
    public StorageData setUpStorages() throws RocksDBException {
        return new StorageData(initStorageData(), initPeers(), initMempool());
    }

    private RocksDB initMempool() throws RocksDBException {
        if(configs.getMempoolPath().mkdirs()) log.info("Creating mempool storage directory:{}",
                configs.getMempoolPath().getAbsolutePath());

        try {
            Options options = new Options();
            options.setCreateIfMissing(true);
            options.setDbLogDir(configs.getMempoolPathLogs().getAbsolutePath());
            log.info("Open storage at:{}", configs.getMempoolPath().getAbsolutePath());

            return RocksDB.open(options, configs.getMempoolPath().getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to create storage for mempool");
            throw new RocksDBException("Exception:" + e);
        }
    }

    private RocksDB initPeers() throws RocksDBException {
        if(configs.getPeersPath().mkdirs()) log.info("Creating peers storage directory:{}",
                configs.getPeersPath().getAbsolutePath());

        try {
            Options options = new Options();
            options.setCreateIfMissing(true);
            options.setDbLogDir(configs.getPeersPathLogs().getAbsolutePath());
            log.info("Open storage at:{}", configs.getPeersPath().getAbsolutePath());

            return RocksDB.open(options, configs.getPeersPath().getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to create storage for peers");
            throw new RocksDBException("Exception:" + e);
        }
    }

    private RocksDB initStorageData() throws RocksDBException {
        if(configs.getBlocksPath().mkdirs()) log.info("Creating blocks&transaction storage directory:{}",
                configs.getBlocksPath().getAbsolutePath());

        try {
            Options options = new Options();
            options.setCreateIfMissing(true);
            options.setDbLogDir(configs.getBlocksPathLogs().getAbsolutePath());
            log.info("Open storage at:{}", configs.getBlocksPath().getAbsolutePath());

            return RocksDB.open(options, configs.getBlocksPath().getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to create storage for blocks&transactions");
            throw new RocksDBException("Exception:" + e);
        }
    }
}
