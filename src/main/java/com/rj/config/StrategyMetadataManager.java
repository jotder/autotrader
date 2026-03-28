package com.rj.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages persistence of strategy version history and drafts to a JSON file.
 */
@Component
public class StrategyMetadataManager {
    private static final Logger log = LoggerFactory.getLogger(StrategyMetadataManager.class);
    private final Path metadataPath;
    private final ObjectMapper mapper;

    public StrategyMetadataManager() {
        this(Path.of("config/strategies/.metadata.json"));
    }

    public StrategyMetadataManager(Path metadataPath) {
        this.metadataPath = metadataPath;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Loads all strategy metadata from disk.
     */
    public Map<String, StrategyVersionMetadata> loadAll() {
        File file = metadataPath.toFile();
        if (!file.exists()) {
            log.info("Metadata file not found, starting with empty metadata: {}", metadataPath);
            return new HashMap<>();
        }

        try {
            return mapper.readValue(file, new TypeReference<HashMap<String, StrategyVersionMetadata>>() {});
        } catch (IOException e) {
            log.error("Failed to load strategy metadata: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Saves all strategy metadata to disk.
     */
    public void saveAll(Map<String, StrategyVersionMetadata> metadata) {
        try {
            File file = metadataPath.toFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            mapper.writeValue(file, metadata);
            log.debug("Saved strategy metadata to {}", metadataPath);
        } catch (IOException e) {
            log.error("Failed to save strategy metadata: {}", e.getMessage());
        }
    }
}
