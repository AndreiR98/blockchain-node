package uk.co.roteala.glaciernode.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Paths;

@Setter
@Getter
@Configuration
public class GlacierConfigs {
    private static final String ROOT_WINDOWS = System.getenv("APPDATA");

    private String rootWindows = ROOT_WINDOWS;

    private static final String PEERS_PATH = "/roteala/peers/";

    private File peersPath = new File(Paths.get(ROOT_WINDOWS, PEERS_PATH).toString());

    private static final String BLOCKS_PATH = "/roteala/data/";

    private File blocksPath = new File(Paths.get(ROOT_WINDOWS, BLOCKS_PATH).toString());

    private static final String MEMPOOL_PATH = "/roteala/mempool/";

    private File mempoolPath = new File(Paths.get(ROOT_WINDOWS, MEMPOOL_PATH).toString());


}
