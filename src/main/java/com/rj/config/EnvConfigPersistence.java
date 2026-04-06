package com.rj.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes key-value pairs to the .env file on disk.
 * Separated from {@link ConfigManager} which is read-only after startup.
 */
@Component
public class EnvConfigPersistence {

    private static final Logger log = LoggerFactory.getLogger(EnvConfigPersistence.class);
    private static final Path ENV_PATH = Path.of(".env");

    /**
     * Updates {@code key} in .env to {@code value}, appending a new line if not present.
     */
    public void update(String key, String value) {
        try {
            List<String> lines = Files.exists(ENV_PATH)
                    ? new ArrayList<>(Files.readAllLines(ENV_PATH))
                    : new ArrayList<>();

            String prefix = key + "=";
            boolean found = false;
            List<String> updated = lines.stream()
                    .map(line -> line.startsWith(prefix) ? prefix + value : line)
                    .collect(Collectors.toCollection(ArrayList::new));

            for (String line : updated) {
                if (line.startsWith(prefix)) { found = true; break; }
            }
            if (!found) updated.add(prefix + value);

            Files.write(ENV_PATH, updated);
            log.info("Updated {} in .env", key);
        } catch (IOException e) {
            log.error("Failed to update {} in .env: {}", key, e.getMessage());
        }
    }
}
