package uk.co.roteala.glaciernode.configs;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.NodeState;
import uk.co.roteala.common.messenger.MessageTemplate;
import uk.co.roteala.common.storage.ColumnFamilyTypes;
import uk.co.roteala.common.storage.StorageTypes;
import uk.co.roteala.glaciernode.storage.Storages;
import uk.co.roteala.net.ConnectionsStorage;
import uk.co.roteala.utils.Constants;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the state cheks if node current state matches the chainState number of blocks
 * */
@Slf4j
public class StateManager {
    private final ConnectionsStorage connectionsStorage;

    private final Storages storages;

    private final Sinks.Many<MessageTemplate> messageTemplateSink;

    @Getter
    private volatile NodeState nodeState;

    @Getter
    private volatile ChainState chainState;

    @Getter
    private Integer target;

    public StateManager (ConnectionsStorage connectionsStorage, Storages storages,
                         Sinks.Many<MessageTemplate> messageTemplateSink) {
        this.connectionsStorage = connectionsStorage;
        this.storages = storages;
        this.messageTemplateSink = messageTemplateSink;

        this.nodeState = (NodeState) storages.getStorage(StorageTypes.STATE)
                .get(ColumnFamilyTypes.NODE, Constants.DEFAULT_NODE_STATE.getBytes(StandardCharsets.UTF_8));
        this.chainState = (ChainState) storages.getStorage(StorageTypes.STATE)
                .get(ColumnFamilyTypes.STATE, Constants.DEFAULT_STATE_NAME.getBytes(StandardCharsets.UTF_8));

        this.target = this.chainState.getTarget();
    }

    @Setter
    @Getter
    private boolean hasMempoolData = false;//set if there are any mempool data

    @Setter
    @Getter
    private boolean brokerConnected = false;//set if the connection with broker is still on

    @Setter
    @Getter
    private boolean hasDataSync = false;//set true when all the statechain is downloaded

    @Setter
    @Getter
    private boolean hasParents = false;

    public boolean isHasParents() {
        return this.connectionsStorage.getAsClientConnections().size() > 0;
    }

    @Setter
    @Getter
    private boolean hasChildren = false;

    public boolean isHasChildren() {
        return this.connectionsStorage.getAsServerConnections().size() > 0;
    }

    @Setter
    @Getter
    private boolean isMining;

    @Setter
    @Getter
    private int miningIndex = 1;

    @Setter
    @Getter
    private AtomicBoolean pauseMining = new AtomicBoolean(false);

    public void verifyState() {
        NodeState nodeState = this.nodeState;

        ChainState chainState = this.chainState;

        //Check number of blocks
        int blocksDifference = chainState.getLastBlockIndex() - nodeState.getLastBlockIndex();
        nodeState.setRemainingBlocks(blocksDifference);

        log.info("Sync status: {} behind", blocksDifference);

        if (blocksDifference == 0) {
            this.hasDataSync = true;
        } else if (blocksDifference > 0) {
            requestData();
        }
    }

    /**
     * Request data from peers
     * */
    private void requestData() {
        int clientsConnections = this.connectionsStorage.getAsClientConnections()
                .size();

        int serversConnections = this.connectionsStorage.getAsServerConnections()
                .size();

        if (brokerConnected && (clientsConnections > 0 || serversConnections > 0)) {
            //request data from peers
        } else if (clientsConnections == 0 && serversConnections == 0) {
            //request data from broker
        }
    }

    /**
     * Status if the miner can work or not
     * */
    public boolean allowMinerToWork() {
        return this.hasDataSync && (this.isHasChildren() || this.isHasParents()) && this.brokerConnected;
    }
}
