package uk.co.roteala.glaciernode.miner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Data
public class MiningWorker {
    private boolean canMine = false;
    private boolean hasStateSync = false;
    private boolean hasDataSync = false;
    private boolean hasConnections = false;
    private boolean hasMempoolData = false;
}
