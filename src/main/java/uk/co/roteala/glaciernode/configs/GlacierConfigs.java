package uk.co.roteala.glaciernode.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

@Setter
@Getter
@Configuration
public class GlacierConfigs {

    private static final String ROOT_WINDOWS = "C:/Blockchain";

    private static final String ROOT_LINUX = "user.home";

    private String rootWindows = ROOT_WINDOWS;

    private String rootLinux = ROOT_LINUX;

    private static final String STORAGE_PATH = "/blocks";

    private String storagePath = STORAGE_PATH;

    private static final String PEERS_PATH = "/peers";

    private String peersPath = PEERS_PATH;

    private static final String WALLET_PATH = "/wallet";

    private String walletPath = WALLET_PATH;

    private static final String MEMEPOOL_PATH = "/memepool";

    private String memepoolPath = MEMEPOOL_PATH;

    private static final String TX_PATH = "/transactions";

    private String txPath = TX_PATH;
}
