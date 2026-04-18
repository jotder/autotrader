package com.rj.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Watches the strategy config directory for YAML file changes and triggers
 * validated hot-reload via {@link YamlStrategyLoader#reloadWithRollback}.
 * <p>
 * Runs on a virtual thread ({@code Thread.ofVirtual()}). On a MODIFY event
 * for any {@code .yaml} or {@code .yml} file in the watched directory:
 * <ol>
 *   <li>Reload all strategies from the configured paths</li>
 *   <li>Validate via {@link ConfigValidator}</li>
 *   <li>If valid → apply new config via the supplied callback; if invalid → rollback (handled by loader)</li>
 * </ol>
 * <p>
 * Contract:
 * <ul>
 *   <li>Invalid config changes are rejected with WARN log; previous config retained.</li>
 *   <li>{@link #stop()} signals shutdown; the watcher thread exits within one poll cycle.</li>
 *   <li>Debounce: rapid successive events within {@code debounceMs} are collapsed to one reload.</li>
 * </ul>
 */
public class ConfigFileWatcher {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileWatcher.class);

    private final Path watchDir;
    private final Path strategiesPath;
    private final Path defaultsPath;
    private final YamlStrategyLoader loader;
    private final ConfigValidator validator;
    private final Consumer<Map<String, StrategyYamlConfig>> onReload;
    private final long debounceMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread watcherThread;

    /**
     * @param watchDir       directory to watch for YAML file changes (e.g. {@code config/strategies/})
     * @param strategiesPath path to the strategies YAML file within watchDir
     * @param defaultsPath   path to the defaults YAML file
     * @param loader         YAML loader with rollback support
     * @param validator      config validator
     * @param onReload       callback invoked with the new valid config after successful reload
     */
    public ConfigFileWatcher(Path watchDir,
                             Path strategiesPath,
                             Path defaultsPath,
                             YamlStrategyLoader loader,
                             ConfigValidator validator,
                             Consumer<Map<String, StrategyYamlConfig>> onReload) {
        this(watchDir, strategiesPath, defaultsPath, loader, validator, onReload, 500L);
    }

    /**
     * Full constructor with configurable debounce interval.
     *
     * @param debounceMs minimum ms between successive reloads (default 500)
     */
    public ConfigFileWatcher(Path watchDir,
                             Path strategiesPath,
                             Path defaultsPath,
                             YamlStrategyLoader loader,
                             ConfigValidator validator,
                             Consumer<Map<String, StrategyYamlConfig>> onReload,
                             long debounceMs) {
        this.watchDir = watchDir;
        this.strategiesPath = strategiesPath;
        this.defaultsPath = defaultsPath;
        this.loader = loader;
        this.validator = validator;
        this.onReload = onReload;
        this.debounceMs = debounceMs;
    }

    /**
     * Starts watching on a virtual thread. Idempotent — calling start() when
     * already running has no effect.
     *
     * @throws IOException if the watch directory cannot be registered
     */
    public void start() throws IOException {
        if (running.getAndSet(true)) {
            log.warn("ConfigFileWatcher already running, ignoring start()");
            return;
        }

        if (!Files.isDirectory(watchDir)) {
            running.set(false);
            throw new IOException("Watch directory does not exist: " + watchDir);
        }

        watcherThread = Thread.ofVirtual()
                .name("config-file-watcher")
                .start(this::watchLoop);
        log.info("ConfigFileWatcher started — watching {} for YAML changes", watchDir);
    }

    /**
     * Signals the watcher to stop. The thread will exit within one poll cycle.
     */
    public void stop() {
        running.set(false);
        Thread t = watcherThread;
        if (t != null) {
            t.interrupt();
            log.info("ConfigFileWatcher stop requested");
        }
    }

    /** Returns {@code true} if the watcher is currently running. */
    public boolean isRunning() {
        return running.get();
    }

    // ── Internal watch loop ──────────────────────────────────────────────────

    private void watchLoop() {
        try (WatchService watchService = watchDir.getFileSystem().newWatchService()) {
            watchDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

            log.debug("WatchService registered on {}", watchDir);

            long lastReloadEpoch = 0;

            while (running.get()) {
                WatchKey key;
                try {
                    key = watchService.take(); // blocks until event or interrupt
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                boolean yamlChanged = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changed = pathEvent.context();
                    String fileName = changed.toString();

                    if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                        log.debug("Detected {} event on: {}", kind.name(), fileName);
                        yamlChanged = true;
                    }
                }

                if (yamlChanged) {
                    long now = System.currentTimeMillis();
                    if (now - lastReloadEpoch >= debounceMs) {
                        lastReloadEpoch = now;
                        performReload();
                    } else {
                        log.debug("Debounced — skipping reload ({}ms since last)", now - lastReloadEpoch);
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    log.warn("WatchKey invalidated — config directory may have been deleted: {}", watchDir);
                    break;
                }
            }
        } catch (IOException e) {
            log.error("ConfigFileWatcher failed to create WatchService: {}", e.getMessage(), e);
        } finally {
            running.set(false);
            log.info("ConfigFileWatcher stopped");
        }
    }

    private void performReload() {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, StrategyYamlConfig> result = loader.reloadWithRollback(
                    strategiesPath, defaultsPath, validator);

            long elapsedMs = System.currentTimeMillis() - startMs;

            if (result == loader.getLastValidConfig() && !result.isEmpty()) {
                // reloadWithRollback returns lastValidConfig — it was either a successful
                // reload (lastValidConfig was updated) or a rollback (old config returned).
                // We always invoke the callback with the current valid config.
                onReload.accept(result);
                log.info("Config hot-reload completed in {}ms ({} strategies)", elapsedMs, result.size());
            } else if (result.isEmpty()) {
                log.warn("Config hot-reload produced empty result — no strategies loaded");
            } else {
                onReload.accept(result);
                log.info("Config hot-reload completed in {}ms ({} strategies)", elapsedMs, result.size());
            }
        } catch (Exception e) {
            log.error("Unexpected error during config hot-reload: {}", e.getMessage(), e);
        }
    }
}
