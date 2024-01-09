package uk.co.roteala.glaciernode.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import uk.co.roteala.common.messenger.MessageContainer;
import uk.co.roteala.common.storage.ColumnFamilyTypes;
import uk.co.roteala.common.storage.DefaultStorage;
import uk.co.roteala.common.storage.Storage;
import uk.co.roteala.common.storage.StorageTypes;
import uk.co.roteala.exceptions.StorageException;
import uk.co.roteala.exceptions.errorcodes.StorageErrorCode;
import uk.co.roteala.glaciernode.configs.NodeConfigs;

import java.util.ArrayList;
import java.util.List;

/**
 * Return all blockchain RocksDB storages
 * */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class Storages  {

    private final NodeConfigs configs;
    /**
     * Represents the storage for the state trie.
     */
    private Storage stateTrieStorage;
    /**
     * Represents the storage for the mempool blockchain.
     */
    private Storage mempoolBlockchain;
    /**
     * Represents the storage for the state blockchain.
     */
    private Storage stateBlockchain;
    /**
     * Represents the storage for peers.
     */
    private Storage peers;

    private Cache<String, MessageContainer> cache;

    /**
     * Retrieves the appropriate storage instance based on the provided {@link StorageTypes}.
     *
     * @param storageType The type of storage required.
     * @return The storage instance corresponding to the provided type.
     * @throws IllegalArgumentException if an unsupported StorageType is provided.
     */
    public Storage getStorage(StorageTypes storageType) {
        switch (storageType) {
            case STATE:
                return this.stateTrieStorage;
            case PEERS:
                return this.peers;
            case MEMPOOL:
                return this.mempoolBlockchain;
            case BLOCKCHAIN:
                return this.stateBlockchain;
            default:
                throw new IllegalArgumentException("Unsupported StorageType: " + storageType);
        }
    }
    /**
     * Initializes the state trie storage. This method ensures that the necessary path exists,
     * sets up the database options, column family options, and descriptors, and then initializes
     * the state trie storage.
     *
     * @throws StorageException if there is any failure during storage initialization.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void initializeStateTrieStorage() {
        try {
            DefaultStorage.ensurePathExists(configs.getStateTriePath());

            DBOptions dbOptions = DefaultStorage.setupDatabaseOptions(true);

            ColumnFamilyOptions columnFamilyOptions = DefaultStorage.setupColumnFamilyOptions();

            List<String> stateTrieColumns = new ArrayList<>();
            stateTrieColumns.add(ColumnFamilyTypes.STATE.getName());
            stateTrieColumns.add(ColumnFamilyTypes.ACCOUNTS.getName());
            stateTrieColumns.add(ColumnFamilyTypes.NODE.getName());

            List<ColumnFamilyDescriptor> columnFamilyDescriptors = DefaultStorage
                    .setupColumnFamilyDescriptors(columnFamilyOptions, stateTrieColumns);

            List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();

            this.stateTrieStorage = new uk.co.roteala.common.storage.Storage(StorageTypes.STATE,
                    RocksDB.open(dbOptions, configs.getStateTriePath().getAbsolutePath(),
                            columnFamilyDescriptors, columnFamilyHandles), columnFamilyHandles);
        } catch (Exception e) {
            log.error("Failed to initialize storage!", e);
            throw new StorageException(StorageErrorCode.STORAGE_FAILED);
        }
    }

    /**
     * Initializes the storage for mempool data.
     * This method ensures the path exists, sets up the database options,
     * column family options, and column family descriptors, and creates a new Storage instance.
     *
     * @throws StorageException if any error occurs during the initialization.
     */
    @Bean
    public void initializeMempoolStorage() {
        try {
            DefaultStorage.ensurePathExists(configs.getMempoolPath());

            DBOptions dbOptions = DefaultStorage.setupDatabaseOptions(true);

            ColumnFamilyOptions columnFamilyOptions = DefaultStorage.setupColumnFamilyOptions();

            List<String> mempoolColumns = new ArrayList<>();
            mempoolColumns.add(ColumnFamilyTypes.TRANSACTIONS.getName());
            mempoolColumns.add(ColumnFamilyTypes.BLOCKS.getName());

            List<ColumnFamilyDescriptor> columnFamilyDescriptors = DefaultStorage
                    .setupColumnFamilyDescriptors(columnFamilyOptions, mempoolColumns);

            List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();

            this.mempoolBlockchain = new uk.co.roteala.common.storage.Storage(StorageTypes.MEMPOOL,
                    RocksDB.open(dbOptions, configs.getMempoolPath().getAbsolutePath(),
                            columnFamilyDescriptors, columnFamilyHandles), columnFamilyHandles);
        } catch (Exception e) {
            log.error("Failed to initialize storage!", e);
            throw new StorageException(StorageErrorCode.STORAGE_FAILED);
        }
    }

    /**
     * Initializes the storage for blockchain data.
     * This method ensures the path exists, sets up the database options,
     * column family options, and column family descriptors, and creates a new Storage instance.
     *
     * @throws StorageException if any error occurs during the initialization.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void initializeBlockchainStorage() {
        try {
            DefaultStorage.ensurePathExists(configs.getBlocksPath());

            DBOptions dbOptions = DefaultStorage.setupDatabaseOptions(true);

            ColumnFamilyOptions columnFamilyOptions = DefaultStorage.setupColumnFamilyOptions();

            List<String> stateBlockchainColumns = new ArrayList<>();
            stateBlockchainColumns.add(ColumnFamilyTypes.TRANSACTIONS.getName());
            stateBlockchainColumns.add(ColumnFamilyTypes.BLOCKS.getName());

            List<ColumnFamilyDescriptor> columnFamilyDescriptors = DefaultStorage
                    .setupColumnFamilyDescriptors(columnFamilyOptions, stateBlockchainColumns);

            List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();

            this.stateBlockchain = new uk.co.roteala.common.storage.Storage(StorageTypes.BLOCKCHAIN,
                    RocksDB.open(dbOptions, configs.getBlocksPath().getAbsolutePath(),
                            columnFamilyDescriptors, columnFamilyHandles), columnFamilyHandles);
        } catch (Exception e) {
            log.error("Failed to initialize storage!", e);
            throw new StorageException(StorageErrorCode.STORAGE_FAILED);
        }
    }

    /**
     * Initializes the storage for peers data.
     * This method ensures the path exists, sets up the database options,
     * and creates a new Storage instance without any specific column families.
     *
     * @throws StorageException if any error occurs during the initialization.
     */
    @Bean
    public void initializePeersStorage() {
        try {
            DefaultStorage.ensurePathExists(configs.getPeersPath());

            Options options = DefaultStorage.setupOptions(false);

            this.peers = new Storage(StorageTypes.PEERS,
                    RocksDB.open(options, configs.getPeersPath().getAbsolutePath()), null);
        } catch (Exception e) {
            log.error("Failed to initialize storage!", e);
            throw new StorageException(StorageErrorCode.STORAGE_FAILED);
        }
    }

    @Bean
    public Cache<String, MessageContainer> initializeCache() {
        return Caffeine.newBuilder()
                //.maximumSize(500)
                .build();
    }
}
