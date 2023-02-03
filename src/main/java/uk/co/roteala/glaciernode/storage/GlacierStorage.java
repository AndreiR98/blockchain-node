package uk.co.roteala.glaciernode.storage;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.SerializationUtils;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.glaciernode.configs.GlacierConfigs;

@Slf4j
@Configuration
public class GlacierStorage {

    @Autowired
    private GlacierConfigs configs;

    private RocksDB storage;

    @Bean
    public void initStorage() throws RocksDBException {
        Options options = new Options();
        options.setCreateIfMissing(true);
        //options.setSkipStatsUpdateOnDbOpen(true);

        log.info("Open storage at path:{}", configs.getStoragePath());

        storage = RocksDB.open(options, configs.getStoragePath());
    }

    public void addAccount(AccountModel account) {
        this.storage.put(SerializationUtils.serialize(account.));
    }
}
