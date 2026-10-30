package com.offerpilot.desktop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prepares the single-user desktop runtime before datasource/Flyway startup:
 * creates the data directories and generates stable local secrets once.
 */
public class DesktopEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("desktop")) {
            return;
        }
        String configuredDir = environment.getProperty("OFFERPILOT_DATA_DIR");
        if (configuredDir == null || configuredDir.isBlank()) {
            configuredDir = System.getProperty("user.home") + "/.offerpilot";
        }
        Path root = Path.of(configuredDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root.resolve("data"));
            Files.createDirectories(root.resolve("uploads"));
            Files.createDirectories(root.resolve("logs"));
            Files.createDirectories(root.resolve("secrets"));

            String jwtSecret = ensureSecret(root.resolve("secrets/jwt.key"));
            String settingsSecret = ensureSecret(root.resolve("secrets/settings.key"));

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("OFFERPILOT_DATA_DIR", root.toString().replace('\\', '/'));
            properties.put("app.jwt.secret", jwtSecret);
            properties.put("app.ai.settings-secret", settingsSecret);
            properties.put("app.storage.local-root", root.resolve("uploads").toString());
            properties.put("logging.file.name", root.resolve("logs/backend.log").toString());
            environment.getPropertySources().addFirst(
                    new MapPropertySource("offerpilotDesktopLocal", properties));
        } catch (IOException ex) {
            throw new IllegalStateException("无法初始化桌面数据目录: " + root, ex);
        }
    }

    private String ensureSecret(Path file) throws IOException {
        if (Files.exists(file)) {
            String existing = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (existing.length() >= 32) {
                return existing;
            }
        }
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        String secret = Base64.getEncoder().encodeToString(bytes);
        Files.writeString(file, secret, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return secret;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
