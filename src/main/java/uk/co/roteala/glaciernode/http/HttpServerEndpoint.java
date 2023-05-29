package uk.co.roteala.glaciernode.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

@Slf4j
@Component
public class HttpServerEndpoint {


//    @Bean
//    public void startHttpServer(){
//        HttpServer server = HttpServer.create()
//                .port(7331)
//                .route(routes ->
//                        routes.get("/block/:index", (request, response) -> handleBlockRequest(request, response))
//                                .get("/transaction/:hash", (request, response) -> handleTransactionRequest(request, response))
//                                .get("/blockchain", (reques, response) -> handleBlockchainRequest(request, response))
//                )
//                .port(8000)
//                .wiretap(true)
//                .bindNow()
//                .onDispose()
//                .block();
//    }

    private static void handleBlockRequest(HttpServerRequest request, HttpServerResponse response){}

    private static void handleTransactionRequest(HttpServerRequest request, HttpServerResponse response){}

    private static void handleBlockchainRequest(HttpServerRequest request, HttpServerResponse response){}
}
