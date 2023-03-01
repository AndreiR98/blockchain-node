package uk.co.roteala.glaciernode.storage;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.glaciernode.configs.GlacierConfigs;
import uk.co.roteala.net.Peer;

@Slf4j
@Component
public class GlacierStorage {

    @Autowired
    private GlacierConfigs configs;

    private RocksDB peers;

    @Bean
    public void initStorage() throws RocksDBException {
        Options options = new Options();
        options.setCreateIfMissing(true);

        log.info("Open storage at path:{}", configs.getStoragePath());

        peers = RocksDB.open(options, configs.getStoragePath());
    }

    public void addPeer(Peer peer) throws RocksDBException {
        final byte[] serializedKey = SerializationUtils.serialize(peer.getAddress());

        this.peers.put(serializedKey, SerializationUtils.serialize(peer));
        this.peers.flush(new FlushOptions().setWaitForFlush(true));
    }


}
