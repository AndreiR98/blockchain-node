package uk.co.roteala.glaciernode.node;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Flux;
import reactor.netty.Connection;
import uk.co.roteala.common.TransactionBaseModel;
import uk.co.roteala.glaciernode.client.PeerCreatorFactory;
import uk.co.roteala.glaciernode.services.PeerServices;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class Node {

    @Autowired
    private PeerCreatorFactory creatorFactory;

    private final PeerServices services;

//    @Bean
//    public void startNode(){
//        final TransactionBaseModel tx = new TransactionBaseModel();
//
//        tx.setTo("test");
//        tx.setTransactionIndex(1);
//        tx.setBlockHash("asdasdasda");
//
//        List<Connection> connections = creatorFactory.connections();
//
//        log.info("Connections:{}", connections);
//
//        connections.forEach(connection -> {
//            log.info("Sending payload!");
//            services.sendPayload(connection.bind(), SerializationUtils.serialize(tx));
//
//            Flux<byte[]> message = services.getPayload(connection.bind());
//            message.doOnNext(t -> {
//                final TransactionBaseModel txd = (TransactionBaseModel) SerializationUtils.deserialize(t);
//                log.info("Test:{}",txd);
//            });
//        });
//
//        //creatorFactory.connections().forEach(connection -> services.sendPayload(connection, SerializationUtils.serialize(tx)));
//    }
}
