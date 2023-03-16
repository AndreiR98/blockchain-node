package uk.co.roteala.glaciernode.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.SerializationUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.netty.Connection;
import uk.co.roteala.common.TransactionBaseModel;
import uk.co.roteala.common.TransactionStatus;
import uk.co.roteala.common.TransactionType;
import uk.co.roteala.common.UTXO;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.glaciernode.client.PeerCreatorFactory;
import uk.co.roteala.glaciernode.services.PeerServices;

import java.security.NoSuchAlgorithmException;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor

@RequestMapping("/blockchain")
public class TxController {
    @Autowired
    private PeerCreatorFactory creatorFactory;

    private final PeerServices services;

    @PostMapping("/tx")
    public void sendTransaction() throws NoSuchAlgorithmException {
        UTXO in = new UTXO();
        in.setAddress("agsbddddddxxx");
        in.setCoinbase(false);
        in.setHeight(1);
        in.setRef("1234444");
        in.setScript("SCRIPT");
        in.setIndex(1);
        in.setValue(new Coin());


        TransactionBaseModel transaction = new TransactionBaseModel();
        transaction.setTransactionIndex(1);
        transaction.setFrom("1111111");
        transaction.setType(TransactionType.TRANSACTION);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setBlockNumber(1);
        transaction.setBlockHash("abcdeff");
        transaction.setTimeStamp(System.currentTimeMillis());
        transaction.setHash(transaction.hashHeader());
        transaction.setIn(List.of(in));
        transaction.setOut(List.of(in));

        List<Connection> connections = creatorFactory.connections();

        connections.forEach(connection -> {
            services.sendPayload(connection, SerializationUtils.serialize(transaction));
        });
    }
}
