package uk.co.roteala.glaciernode.client;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;
import reactor.netty.tcp.TcpClient;
import uk.co.roteala.net.Peer;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;


/**
 * Client class that handles all the connections between peers
 * */
@Component
@Slf4j
public class Client {
}
