//package uk.co.roteala.glaciernode.p2p;
//
//import lombok.Getter;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//import reactor.netty.Connection;
//import uk.co.roteala.glaciernode.storage.StorageServices;
//import uk.co.roteala.net.Peer;
//import uk.co.roteala.utils.BlockchainUtils;
//
//import java.util.List;
//import java.util.function.Consumer;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class ClientConnectionStorage implements Consumer<Connection> {
//    @Getter
//    private List<Connection> clientConnections;
//
//    @Autowired
//    private StorageServices storage;
//
//    @Override
//    public void accept(Connection connection) {
//        this.clientConnections.add(connection);
//        log.info("Peer connected!");
//
//        Peer peer = new Peer();
//        peer.setPort(7331);
//        peer.setAddress(BlockchainUtils.formatIPAddress(connection.address()));
//        peer.setActive(true);
//        this.storage.addPeer(peer);
//    }
//}
