package uk.co.roteala.glaciernode.node;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import reactor.netty.Connection;
import uk.co.roteala.glaciernode.client.PeerCreatorFactory;
import uk.co.roteala.glaciernode.client.Seeder;
import uk.co.roteala.glaciernode.server.Server;
import uk.co.roteala.glaciernode.services.ConnectionServices;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class Node {

    private PeerCreatorFactory creatorFactory;

//    @Autowired
//    private Seeder seeder;
    private List<Connection> connnections;

    /**
     * Start the seeder and all the connections between nodes
     * */
    @Bean
    public void startNode() throws InterruptedException, RocksDBException {
        Server server = new Server();

        server.initServer();


        Seeder seeder = new Seeder();
        seeder.seederConnection();

        Thread.sleep(1000);

        creatorFactory.peersConnection();

        this.connnections = creatorFactory.getP2P();

    }
}
