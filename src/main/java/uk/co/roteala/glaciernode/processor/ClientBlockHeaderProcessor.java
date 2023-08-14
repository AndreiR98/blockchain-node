package uk.co.roteala.glaciernode.processor;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import uk.co.roteala.common.Block;
import uk.co.roteala.common.BlockHeader;
import uk.co.roteala.common.BlockStatus;
import uk.co.roteala.common.ChainState;
import uk.co.roteala.common.events.MessageActions;
import uk.co.roteala.common.events.MessageTypes;
import uk.co.roteala.common.events.MessageWrapper;
import uk.co.roteala.exceptions.MiningException;
import uk.co.roteala.exceptions.errorcodes.MiningErrorCode;
import uk.co.roteala.glaciernode.miner.MiningWorker;
import uk.co.roteala.glaciernode.p2p.BrokerConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ClientConnectionStorage;
import uk.co.roteala.glaciernode.p2p.ServerConnectionStorage;
import uk.co.roteala.glaciernode.storage.StorageServices;

import java.util.ArrayList;
import java.util.Objects;

@Slf4j
@Component
@AllArgsConstructor
@NoArgsConstructor
public class ClientBlockHeaderProcessor {
    private BlockHeader blockHeader;
    private Connection incomingClientConnection;

    @Autowired
    private StorageServices storage;

    @Autowired
    private ClientConnectionStorage clientConnectionStorage;

    @Autowired
    private ServerConnectionStorage serverConnectionStorage;

    @Autowired
    private BrokerConnectionStorage brokerConnectionStorage;

    @Autowired
    private MiningWorker worker;

    /**
     * Verify incoming header from a miner if OK then send to it's childrens serverConnections
     * Send confirmation to broker and add the block to the memblock
     * Since we know from where does the message is coming from exclude that connection if it's children.
     * */
    public void verifyNewBlock() {
        try {
            ChainState state = storage.getStateTrie();

            Block prevBlock = state.getLastBlockIndex() <= 0 ? state.getGenesisBlock()
                    : storage.getBlockByIndex(state.getLastBlockIndex());

            if(this.blockHeader == null) {
                throw new MiningException(MiningErrorCode.BLOCK_NOT_FOUND);
            }

            if(this.storage.getPseudoBlockByHash(this.blockHeader.getHash()) != null) {
                throw new MiningException(MiningErrorCode.ALREADY_EXISTS);
            }

            if(!Objects.equals(prevBlock.getHash(), this.blockHeader.getPreviousHash())){
                log.info("Could not match with a previous hash:{} with:{}!", prevBlock.getHash(),
                        this.blockHeader.getPreviousHash());
                throw new MiningException(MiningErrorCode.PREVIOUS_HASH);
            }

            if(Objects.equals(blockHeader.getMarkleRoot(),
                    "0000000000000000000000000000000000000000000000000000000000000000")){
                processEmptyBlock();
            } else {
                processTransactionalBlock();
            }

            log.info("New block:{} validated!", blockHeader.getHash());
        } catch (Exception e) {
            log.error("Error while processing new header:{}", e.getMessage());
        }
    }

    //public syncWithNewHeader() {}

    private void processEmptyBlock() {
        try {
            Block block = new Block();
            block.setHeader(blockHeader);
            block.setTransactions(new ArrayList<>());
            block.setStatus(BlockStatus.PENDING);
            block.setForkHash("0000000000000000000000000000000000000000000000000000000000000000");
            block.setConfirmations(1);
            block.setNumberOfBits(SerializationUtils.serialize(block).length);

            storage.addBlockMempool(block.getHash(), block);

            this.worker.setPauseMining(true);

            //Send confirmation to broker
            MessageWrapper brokerWrapper = new MessageWrapper();
            brokerWrapper.setAction(MessageActions.VERIFIED_MINED_BLOCK);
            brokerWrapper.setVerified(true);
            brokerWrapper.setContent(this.blockHeader);
            brokerWrapper.setType(MessageTypes.BLOCKHEADER);

            this.brokerConnectionStorage.getConnection()
                    .outbound().sendObject(Mono.just(brokerWrapper))
                    .then().subscribe();

            MessageWrapper clientsWrapper = new MessageWrapper();
            clientsWrapper.setType(MessageTypes.BLOCKHEADER);
            clientsWrapper.setVerified(true);
            clientsWrapper.setContent(this.blockHeader);
            clientsWrapper.setAction(MessageActions.MINED_BLOCK);

            //Broadcast the block to children peers
            for(Connection connection : this.serverConnectionStorage.getServerConnections()) {
                //Check if sender is connections
                if(!connection.address().equals(this.incomingClientConnection.address())) {
                    connection.outbound()
                            .sendObject(Mono.just(clientsWrapper.serialize()))
                            .then().subscribe();
                }
            }
        } catch (Exception e) {
            log.error("Error while processing empty block:{}", e.getMessage());
        }
    }

    private void processTransactionalBlock() {}
}
