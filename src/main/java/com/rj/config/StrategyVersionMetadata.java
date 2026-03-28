package com.rj.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal metadata for a single strategy, including history and optional draft.
 */
public class StrategyVersionMetadata {
    private int activeVersion = 1;
    private int latestVersion = 1;
    private List<VersionEntry> history = new ArrayList<>();
    private StrategyYamlConfig draftConfig;

    public int getActiveVersion() { return activeVersion; }
    public void setActiveVersion(int activeVersion) { this.activeVersion = activeVersion; }

    public int getLatestVersion() { return latestVersion; }
    public void setLatestVersion(int latestVersion) { this.latestVersion = latestVersion; }

    public List<VersionEntry> getHistory() { return history; }
    public void setHistory(List<VersionEntry> history) { this.history = history; }

    public StrategyYamlConfig getDraftConfig() { return draftConfig; }
    public void setDraftConfig(StrategyYamlConfig draftConfig) { this.draftConfig = draftConfig; }
}
