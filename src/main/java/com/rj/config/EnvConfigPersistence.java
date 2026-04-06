package com.rj.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
     *
     * @throws UncheckedIOException if the file cannot be read or written
     */
    public void update(String key, String value) {
        try {
            List<String> lines = Files.exists(ENV_PATH)
                    ? new ArrayList<>(Files.readAllLines(ENV_PATH))
                    : new ArrayList<>();

            String prefix = key + "=";
            boolean found = false;
            List<String> updated = new ArrayList<>();
            for (String line : lines) {
                if (line.startsWith(prefix)) {
                    updated.add(prefix + value);
                    found = true;
                } else {
                    updated.add(line);
                }
            }
            if (!found) updated.add(prefix + value);

            Files.write(ENV_PATH, updated);
            log.debug(".env persistence: updated key");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist key to .env", e);
        }
    }
}
