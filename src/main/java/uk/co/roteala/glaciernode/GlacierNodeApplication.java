package uk.co.roteala.glaciernode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import uk.co.roteala.glaciernode.configs.NodeConfigs;

@SpringBootApplication
public class GlacierNodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlacierNodeApplication.class, args);
    }

}
