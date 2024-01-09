package uk.co.roteala.glaciernode;

import io.vertx.core.Vertx;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import uk.co.roteala.glaciernode.client.ClientInitializer;
import uk.co.roteala.glaciernode.configs.NodeConfigs;
import uk.co.roteala.glaciernode.services.ServerInitializer;

@SpringBootApplication
public class GlacierNodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlacierNodeApplication.class, args);
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> deployVerticle(Vertx vertx, ServerInitializer verticle) {
        return event -> vertx.deployVerticle(verticle);
    }
}
