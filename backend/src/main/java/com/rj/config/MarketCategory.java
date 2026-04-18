package com.rj.config;

/**
 * Market categories for grouping symbols in the global symbol registry.
 * Maps to top-level keys in {@code config/symbols.yaml}.
 */
public enum MarketCategory {

    CM("cm", "Capital Market"),
    FO("fo", "Equity Derivatives"),
    COM("com", "Commodity Derivatives");

    private final String yamlKey;
    private final String displayName;

    MarketCategory(String yamlKey, String displayName) {
        this.yamlKey = yamlKey;
        this.displayName = displayName;
    }

    public String yamlKey() {
        return yamlKey;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Resolve a YAML key (e.g. "cm", "fo", "com") to its enum constant.
     *
     * @throws IllegalArgumentException if the key is not recognized
     */
    public static MarketCategory fromYamlKey(String key) {
        if (key == null) throw new IllegalArgumentException("Market category key must not be null");
        for (MarketCategory mc : values()) {
            if (mc.yamlKey.equalsIgnoreCase(key.trim())) return mc;
        }
        throw new IllegalArgumentException("Unknown market category: '" + key + "'. Valid keys: cm, fo, com");
    }
}
