package com.offerpilot.resume.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class StorageConfig {

    @Bean
    public FileStorage fileStorage(
            @Value("${app.storage.local-root:./data/uploads}") String localRoot,
            @Value("${app.storage.max-size:10485760}") long maxSize) {
        return new LocalFileStorage(Path.of(localRoot), maxSize);
    }
}
