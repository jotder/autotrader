package com.rj.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OptionChainConfig {

    private static final Logger log = LoggerFactory.getLogger(OptionChainConfig.class);
    private static final Path CONFIG_PATH = Path.of("config/option-chain.yaml");

    private boolean enabled = true;
    private int pollIntervalSeconds = 30;
    private int strikeCount = 10;
    private int staleThresholdSeconds = 90;
    private double refreshRelVolThreshold = 1.5;
    private double refreshConfidenceThreshold = 0.85;
    private String archivePath = "data/option-chain";
    private List<String> underlyings = new ArrayList<>();

    @PostConstruct
    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            log.info("option-chain.yaml not found at {} — using defaults", CONFIG_PATH);
            return;
        }
        try (InputStream is = Files.newInputStream(CONFIG_PATH)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            if (root == null) return;
            Object cfg = root.get("option-chain");
            if (!(cfg instanceof Map<?, ?> m)) return;

            if (m.get("enabled") instanceof Boolean b)           this.enabled = b;
            if (m.get("poll-interval-seconds") instanceof Integer i) this.pollIntervalSeconds = i;
            if (m.get("strike-count") instanceof Integer i)      this.strikeCount = i;
            if (m.get("stale-threshold-seconds") instanceof Integer i) this.staleThresholdSeconds = i;
            if (m.get("refresh-rel-vol-threshold") instanceof Number n) this.refreshRelVolThreshold = n.doubleValue();
            if (m.get("refresh-confidence-threshold") instanceof Number n) this.refreshConfidenceThreshold = n.doubleValue();
            if (m.get("archive-path") instanceof String s)       this.archivePath = s;
            if (m.get("underlyings") instanceof List<?> list)    this.underlyings = list.stream()
                    .filter(String.class::isInstance).map(String.class::cast).toList();

            log.info("OptionChainConfig loaded: enabled={}, pollInterval={}s, strikeCount={}, underlyings={}",
                    enabled, pollIntervalSeconds, strikeCount,
                    underlyings.isEmpty() ? "auto-discover" : underlyings);
        } catch (Exception e) {
            log.error("Failed to load option-chain.yaml — using defaults: {}", e.getMessage());
        }
    }

    public boolean isEnabled()                    { return enabled; }
    public int getPollIntervalSeconds()            { return pollIntervalSeconds; }
    public int getStrikeCount()                    { return strikeCount; }
    public int getStaleThresholdSeconds()          { return staleThresholdSeconds; }
    public double getRefreshRelVolThreshold()      { return refreshRelVolThreshold; }
    public double getRefreshConfidenceThreshold()  { return refreshConfidenceThreshold; }
    public String getArchivePath()                 { return archivePath; }
    public List<String> getUnderlyings()           { return underlyings; }

    /** Test-only setter — bypasses YAML loading. */
    void setUnderlyingsForTest(java.util.List<String> underlyings) {
        this.underlyings = underlyings;
    }
}
