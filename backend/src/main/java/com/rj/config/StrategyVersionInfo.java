package com.rj.config;

import java.util.List;

/**
 * Composite object containing active config, draft config, and history.
 * Returned to the frontend for the Strategy Configuration UI.
 */
public record StrategyVersionInfo(
    String strategyId,
    int activeVersion,
    int latestVersion,
    boolean enabled,
    String status, // ACTIVE, HAS_DRAFT, DISABLED
    List<VersionEntry> history,
    StrategyYamlConfig config,        // Active version config
    StrategyYamlConfig draftConfig    // Draft config if exists
) {}
