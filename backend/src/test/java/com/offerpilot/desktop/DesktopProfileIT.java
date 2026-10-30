package com.offerpilot.desktop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "OFFERPILOT_DATA_DIR=target/desktop-test-data",
        "server.port=0"
})
@ActiveProfiles("desktop")
class DesktopProfileIT {

    @Autowired
    private Environment environment;

    @Test
    void startsWithLocalFileDatabaseAndStableSecrets() {
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("app.jwt.secret")).isNotBlank();
        assertThat(environment.getProperty("app.ai.settings-secret")).isNotBlank();

        Path dataDir = Path.of("target/desktop-test-data");
        assertThat(Files.exists(dataDir.resolve("data/offerpilot.mv.db"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("secrets/jwt.key"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("secrets/settings.key"))).isTrue();
    }
}
