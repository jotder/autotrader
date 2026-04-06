package com.rj.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EnvConfigPersistenceTest {

    private EnvConfigPersistence persistence(Path envFile) {
        EnvConfigPersistence p = new EnvConfigPersistence();
        p.ENV_PATH = envFile;
        return p;
    }

    @Test
    void update_existingKey_updatesInPlace(@TempDir Path dir) throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "FOO=old\nBAR=keep\n");

        persistence(env).update("FOO", "new");

        List<String> lines = Files.readAllLines(env);
        assertThat(lines).contains("FOO=new").contains("BAR=keep").doesNotContain("FOO=old");
    }

    @Test
    void update_missingKey_appendsLine(@TempDir Path dir) throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "FOO=bar\n");

        persistence(env).update("NEW_KEY", "value");

        List<String> lines = Files.readAllLines(env);
        assertThat(lines).contains("NEW_KEY=value").contains("FOO=bar");
    }

    @Test
    void update_fileAbsent_createsFile(@TempDir Path dir) {
        Path env = dir.resolve(".env");

        persistence(env).update("KEY", "val");

        assertThat(env).exists();
        assertThat(env).content().contains("KEY=val");
    }

    @Test
    void update_nonWritablePath_throwsUncheckedIOException(@TempDir Path dir) {
        Path nonWritable = dir.resolve("missing_parent").resolve(".env");

        assertThatThrownBy(() -> persistence(nonWritable).update("K", "v"))
                .isInstanceOf(UncheckedIOException.class);
    }
}
