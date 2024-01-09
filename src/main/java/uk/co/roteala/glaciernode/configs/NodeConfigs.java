package uk.co.roteala.glaciernode.configs;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Paths;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "roteala.blockchain")
public class NodeConfigs {
    //private static final String ROOT_WINDOWS = "/blockchain/node-"+System.getenv("POD_ORDINAL_INDEX");
    private static final String ROOT_WINDOWS = System.getProperty("user.home");

    //private static final String ROOT_WINDOWS = System.getProperty("user.home");

    private String rootWindows = ROOT_WINDOWS;

    private static final String LOGS = "/logs";

    private static final String PEERS_PATH = "/roteala/peers/";

    private File peersPath = new File(Paths.get(ROOT_WINDOWS, PEERS_PATH).toString());

    private File peersPathLogs = new File(Paths.get(ROOT_WINDOWS, PEERS_PATH, LOGS).toString());

    private static final String BLOCKS_PATH = "/roteala/data/";

    private File blocksPath = new File(Paths.get(ROOT_WINDOWS, BLOCKS_PATH).toString());

    private File blocksPathLogs = new File(Paths.get(ROOT_WINDOWS, BLOCKS_PATH, LOGS).toString());

    private static final String MEMPOOL_PATH = "/roteala/mempool/";

    private File mempoolPath = new File(Paths.get(ROOT_WINDOWS, MEMPOOL_PATH).toString());

    private File mempoolPathLogs = new File(Paths.get(ROOT_WINDOWS, MEMPOOL_PATH, LOGS).toString());

    private static final String STATE_TRIE = "/roteala/state";

    private File stateTriePath = new File(Paths.get(ROOT_WINDOWS, STATE_TRIE).toString());

    private File stateTrieLogsPath = new File(Paths.get(ROOT_WINDOWS, STATE_TRIE, LOGS).toString());

    private String minerPrivateKey;

    private String nodeServerIP;

    public static int DEFAULT_CONNECTION_ALLOWED = 5;
}
