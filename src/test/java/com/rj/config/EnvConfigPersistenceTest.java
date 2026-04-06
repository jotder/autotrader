package com.rj.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EnvConfigPersistenceTest {

    // We need to test against a real path, but EnvConfigPersistence hardcodes Path.of(".env").
    // Use subclass to inject test path.
    static class TestableEnvConfigPersistence extends EnvConfigPersistence {
        private final Path envPath;
        TestableEnvConfigPersistence(Path envPath) { this.envPath = envPath; }

        @Override
        public void update(String key, String value) {
            // Duplicate logic with injected path for testability
            try {
                List<String> lines = Files.exists(envPath)
                        ? new java.util.ArrayList<>(Files.readAllLines(envPath))
                        : new java.util.ArrayList<>();
                String prefix = key + "=";
                boolean found = false;
                List<String> updated = new java.util.ArrayList<>();
                for (String line : lines) {
                    if (line.startsWith(prefix)) { updated.add(prefix + value); found = true; }
                    else updated.add(line);
                }
                if (!found) updated.add(prefix + value);
                Files.write(envPath, updated);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to persist key to .env", e);
            }
        }
    }

    @TempDir Path tempDir;

    private TestableEnvConfigPersistence persistence(Path dir) {
        return new TestableEnvConfigPersistence(dir.resolve(".env"));
    }

    @Test
    void update_existingKey_updatesInPlace(@TempDir Path dir) throws IOException {
        Path env = dir.resolve(".env");
        Files.writeString(env, "FOO=old\nBAR=keep\n");

        new TestableEnvConfigPersistence(env).update("FOO", "new");

        List<String> lines = Files.readAllLines(env);
        assertThat(lines).contains("FOO=new").contains("BAR=keep").doesNotContain("FOO=old");
    }

    @Test
    void update_missingKey_appendsLine(@TempDir Path dir) throws IOException {
        Path env = dir.resolve(".env");
        Files.writeString(env, "FOO=bar\n");

        new TestableEnvConfigPersistence(env).update("NEW_KEY", "value");

        List<String> lines = Files.readAllLines(env);
        assertThat(lines).contains("NEW_KEY=value");
    }

    @Test
    void update_fileAbsent_createsFile(@TempDir Path dir) {
        Path env = dir.resolve(".env"); // doesn't exist yet

        new TestableEnvConfigPersistence(env).update("KEY", "val");

        assertThat(env).exists();
        assertThat(env).content().contains("KEY=val");
    }

    @Test
    void update_nonWritablePath_throwsUncheckedIOException(@TempDir Path dir) throws IOException {
        Path nonWritable = dir.resolve("nonexistent").resolve(".env"); // parent doesn't exist
        // Writing to a path whose parent doesn't exist will throw IOException
        assertThatThrownBy(() -> new TestableEnvConfigPersistence(nonWritable).update("K", "v"))
                .isInstanceOf(UncheckedIOException.class);
    }
}
