package uk.co.roteala.glaciernode.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.co.roteala.common.MockAccount;
import uk.co.roteala.glaciernode.security.ECKey;
import uk.co.roteala.glaciernode.storage.StorageServices;
import uk.co.roteala.net.Peer;
import uk.co.roteala.utils.GlacierUtils;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/blockchain")
public class PeersController {
    private final StorageServices storage;

    @GetMapping
    public ResponseEntity<List<Peer>> getPeers() throws RocksDBException {
        List<Peer> peerList = storage.getPeersFromStorage();
        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(peerList, headers, HttpStatus.OK);
    }

    @GetMapping("/account")
    public ResponseEntity<MockAccount> mockAccount() throws RocksDBException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        MockAccount account = new MockAccount();

        ECKey key = new ECKey();

        account.setAddress(key.getPublicKey().toAddress());
        account.setPrivateKey(key.getPrivateKey().getHex());
        account.setWif(key.getPrivateKey().toWIF());
        account.setScriptKey(key.getPrivateKey().getHex());

        HttpHeaders headers = new HttpHeaders();

        return new ResponseEntity<>(account, headers, HttpStatus.OK);
    }
}
