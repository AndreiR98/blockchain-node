package uk.co.roteala.glaciernode.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

@Setter
@Getter
@Configuration
public class GlacierConfigs {
    private static final String STORAGE_PATH = "C:/Glacier";

    private String storagePath = STORAGE_PATH;
}
