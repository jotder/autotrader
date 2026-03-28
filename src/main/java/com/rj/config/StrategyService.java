package com.rj.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing strategy configurations, versions, and drafts.
 */
@Service
public class StrategyService {
    private static final Logger log = LoggerFactory.getLogger(StrategyService.class);
    private final Path strategiesPath;
    private final Path defaultsPath;

    private final YamlStrategyLoader loader;
    private final YamlStrategyWriter writer;
    private final StrategyMetadataManager metadataManager;
    private final ConfigValidator validator;

    @Autowired
    public StrategyService(StrategyMetadataManager metadataManager, SymbolRegistry symbolRegistry) {
        this(metadataManager, symbolRegistry, Path.of("config/strategies/intraday.yaml"), Path.of("config/defaults.yaml"));
    }

    public StrategyService(StrategyMetadataManager metadataManager, SymbolRegistry symbolRegistry,
                           Path strategiesPath, Path defaultsPath) {
        this.loader = new YamlStrategyLoader();
        this.writer = new YamlStrategyWriter();
        this.metadataManager = metadataManager;
        this.validator = new ConfigValidator(symbolRegistry);
        this.strategiesPath = strategiesPath;
        this.defaultsPath = defaultsPath;
    }

    /**
     * Retrieves all strategies with their versioning info.
     */
    public List<StrategyVersionInfo> getAllStrategies() {
        Map<String, StrategyYamlConfig> activeConfigs = loader.load(strategiesPath);
        Map<String, StrategyVersionMetadata> allMetadata = metadataManager.loadAll();

        List<StrategyVersionInfo> result = new ArrayList<>();
        
        // Merge active configs with metadata
        Set<String> allIds = new HashSet<>(activeConfigs.keySet());
        allIds.addAll(allMetadata.keySet());

        for (String id : allIds) {
            StrategyYamlConfig active = activeConfigs.get(id);
            StrategyVersionMetadata meta = getOrInitMetadata(id, active, allMetadata);
            result.add(buildInfo(id, active, meta));
        }

        return result.stream()
                .sorted(Comparator.comparing(StrategyVersionInfo::strategyId))
                .collect(Collectors.toList());
    }

    public StrategyVersionInfo getStrategy(String id) {
        Map<String, StrategyYamlConfig> activeConfigs = loader.load(strategiesPath);
        Map<String, StrategyVersionMetadata> allMetadata = metadataManager.loadAll();
        
        StrategyYamlConfig active = activeConfigs.get(id);
        StrategyVersionMetadata meta = getOrInitMetadata(id, active, allMetadata);
        
        if (active == null && meta.getHistory().isEmpty() && meta.getDraftConfig() == null) {
            return null;
        }
        
        return buildInfo(id, active, meta);
    }

    private StrategyVersionMetadata getOrInitMetadata(String id, StrategyYamlConfig active, Map<String, StrategyVersionMetadata> allMetadata) {
        StrategyVersionMetadata meta = allMetadata.get(id);
        boolean created = false;
        if (meta == null) {
            meta = new StrategyVersionMetadata();
            allMetadata.put(id, meta);
            created = true;
        }
        
        // If it's active but has no history, create initial history
        if (active != null && meta.getHistory().isEmpty()) {
            meta.getHistory().add(new VersionEntry(1, VersionState.ACTIVE, Instant.now(), "Initial version"));
            meta.setActiveVersion(1);
            meta.setLatestVersion(1);
            created = true;
        }
        
        if (created) {
            metadataManager.saveAll(allMetadata);
        }
        return meta;
    }

    public void createDraft(String id) {
        Map<String, StrategyVersionMetadata> allMetadata = metadataManager.loadAll();
        StrategyVersionMetadata meta = allMetadata.computeIfAbsent(id, k -> new StrategyVersionMetadata());
        
        Map<String, StrategyYamlConfig> activeConfigs = loader.load(strategiesPath);
        StrategyYamlConfig active = activeConfigs.get(id);
        
        if (active == null) {
            // New strategy draft from scratch (use defaults)
            meta.setDraftConfig(loader.loadDefaults(defaultsPath));
        } else {
            // Copy active to draft
            meta.setDraftConfig(active);
        }
        
        metadataManager.saveAll(allMetadata);
    }

    public void updateDraft(String id, StrategyYamlConfig config) {
        Map<String, StrategyVersionMetadata> allMetadata = metadataManager.loadAll();
        StrategyVersionMetadata meta = allMetadata.get(id);
        if (meta == null) throw new IllegalArgumentException("Strategy metadata not found: " + id);
        
        meta.setDraftConfig(config);
        metadataManager.saveAll(allMetadata);
    }

    public void promoteDraft(String id) throws IOException {
        Map<String, StrategyVersionMetadata> allMetadata = metadataManager.loadAll();
        StrategyVersionMetadata meta = allMetadata.get(id);
        if (meta == null || meta.getDraftConfig() == null) {
            throw new IllegalStateException("No draft found to promote for: " + id);
        }

        // 1. Update active config map
        Map<String, StrategyYamlConfig> activeConfigs = new LinkedHashMap<>(loader.load(strategiesPath));
        activeConfigs.put(id, meta.getDraftConfig());

        // 2. Save to YAML
        writer.save(strategiesPath, activeConfigs);

        // 3. Update metadata
        int newVersion = meta.getLatestVersion() + 1;
        
        // Archive current active
        meta.getHistory().stream()
                .filter(v -> v.state() == VersionState.ACTIVE)
                .findFirst()
                .ifPresent(v -> {
                    int idx = meta.getHistory().indexOf(v);
                    meta.getHistory().set(idx, new VersionEntry(v.version(), VersionState.ARCHIVED, v.createdAt(), v.note()));
                });

        // Add new active
        meta.getHistory().add(new VersionEntry(newVersion, VersionState.ACTIVE, Instant.now(), "Promoted from draft"));
        meta.setActiveVersion(newVersion);
        meta.setLatestVersion(newVersion);
        meta.setDraftConfig(null);

        metadataManager.saveAll(allMetadata);
        log.info("Promoted strategy '{}' to version {}", id, newVersion);
    }

    public void toggleStrategy(String id) throws IOException {
        Map<String, StrategyYamlConfig> activeConfigs = new LinkedHashMap<>(loader.load(strategiesPath));
        StrategyYamlConfig cfg = activeConfigs.get(id);
        if (cfg == null) throw new IllegalArgumentException("Strategy not found: " + id);
        
        cfg.setEnabled(!cfg.isEnabled());
        writer.save(strategiesPath, activeConfigs);
        log.info("Toggled strategy '{}' to enabled={}", id, cfg.isEnabled());
    }

    public void duplicateStrategy(String id, String newId) throws IOException {
        Map<String, StrategyYamlConfig> activeConfigs = new LinkedHashMap<>(loader.load(strategiesPath));
        StrategyYamlConfig source = activeConfigs.get(id);
        if (source == null) throw new IllegalArgumentException("Source strategy not found: " + id);
        
        activeConfigs.put(newId, source);
        writer.save(strategiesPath, activeConfigs);
        
        // Create initial metadata for the clone
        Map<String, StrategyVersionMetadata> allMetadata = metadataManager.loadAll();
        StrategyVersionMetadata meta = new StrategyVersionMetadata();
        meta.getHistory().add(new VersionEntry(1, VersionState.ACTIVE, Instant.now(), "Cloned from " + id));
        allMetadata.put(newId, meta);
        metadataManager.saveAll(allMetadata);
        
        log.info("Duplicated strategy '{}' to '{}'", id, newId);
    }

    public StrategyYamlConfig getDefaults() {
        return loader.loadDefaults(defaultsPath);
    }

    public ConfigValidator.ValidationResult validate(StrategyYamlConfig config) {
        return validator.validate(config);
    }

    private StrategyVersionInfo buildInfo(String id, StrategyYamlConfig active, StrategyVersionMetadata meta) {
        boolean enabled = active != null && active.isEnabled();
        String status = "DISABLED";
        if (active != null) {
            status = meta.getDraftConfig() != null ? "HAS_DRAFT" : "ACTIVE";
        }

        return new StrategyVersionInfo(
                id,
                meta.getActiveVersion(),
                meta.getLatestVersion(),
                enabled,
                status,
                meta.getHistory(),
                active != null ? active : loader.loadDefaults(defaultsPath),
                meta.getDraftConfig()
        );
    }
}
