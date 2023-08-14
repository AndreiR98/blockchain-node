package uk.co.roteala.glaciernode.miner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Miner worker, can start mining blocks if
 * isBrokerConnected = true
 * hasMempoolData = true
 * hashStateSync = true
 * hashDataSync = true;
 * */
@Slf4j
@Data
public class MiningWorker {
    /**
     * If the node is not acting as a server for others then it won't directly broadcast to them
     * instead it will broadcast to parents and parents will broadcast to others
     *                    N
     *                    |
     *                  A   B
     *                  |  / \
     *                  C    D
     *
     * Lets say D mines a block and it's connected only to B, it will send data to B
     * Then B will broadcast it to C and N, N will broadcast it to A, Not B(because of back sending), A will broadcast to C
     * But C already has the block from B, and thus will not propagate or backsend it anymore
     * Once a block arrives in the mining queue it won't be broadcasted again this way we eliminate infite loops
     *
     *                    N
     *                    |
     *                  A   B
     *                  |   | \
     *                  C   D  E
     *                      \ /
     *                       F
     * Let's say E mines a block and that broadcast it to F and backsend to B
     * F will backsend to D, D will backsend to B
     * B will Send to D and backsend to N
     * N will send to A, and A to C
     * We can see that B,D,E,F can create an infite loop
     * The catch is to break the loop in F, So F gets the block from E, it will check for peer to broadcast further, if no possible
     * then it will verify the block and confirm the broker.
     * The block will then go to B, and B will send to D, D will send to F, but F already have it
     *
     *                    N
     *                    |
     *                  A   B
     *                  |   | \
     *                  C   D  E
     *                      \ /
     *                       F
     *                       |
     *                       G
     *
     *
     * */
    //private boolean hasConnections = false;
    private boolean hasMempoolData = false;//set if there are any mempool data
    private boolean brokerConnected = false;//set if the connection with broker is still on
    private boolean hasStateSync = false;//Set true when received synchronized statechain
    private boolean hasDataSync = false;//set true when all the statechain is downloaded
    private boolean hasParents = false;
    private boolean hasChildren = false;
    private boolean isMining;
    private int miningIndex = 1;
    private volatile boolean pauseMining = false;



}
