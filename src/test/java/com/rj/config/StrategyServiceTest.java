package com.rj.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StrategyServiceTest {

    @TempDir
    Path tempDir;

    private StrategyService service;
    private Path strategiesPath;
    private Path metadataPath;
    private Path defaultsPath;

    @BeforeEach
    void setUp() throws IOException {
        strategiesPath = tempDir.resolve("intraday.yaml");
        metadataPath = tempDir.resolve(".metadata.json");
        defaultsPath = tempDir.resolve("defaults.yaml");
        Path symbolsPath = tempDir.resolve("symbols.yaml");

        Files.writeString(strategiesPath, "strategies:\n  test_strat:\n    enabled: true\n    symbols: [\"NSE:SBIN-EQ\"]\n");
        Files.writeString(defaultsPath, "defaults:\n  timeframe: M5\n");
        Files.writeString(symbolsPath, "symbols:\n  cm:\n    - \"NSE:SBIN-EQ\"\n");

        StrategyMetadataManager metadataManager = new StrategyMetadataManager(metadataPath);
        SymbolRegistry registry = SymbolRegistry.load(symbolsPath);

        service = new StrategyService(metadataManager, registry, strategiesPath, defaultsPath);
    }
    @Test
    void testGetAllStrategies() {
        List<StrategyVersionInfo> all = service.getAllStrategies();
        assertEquals(1, all.size());
        assertEquals("test_strat", all.get(0).strategyId());
        assertEquals(1, all.get(0).activeVersion());
        assertEquals("ACTIVE", all.get(0).status());
    }

    @Test
    void testCreateAndUpdateDraft() {
        service.createDraft("test_strat");
        StrategyVersionInfo info = service.getStrategy("test_strat");
        assertEquals("HAS_DRAFT", info.status());
        assertNotNull(info.draftConfig());

        StrategyYamlConfig draft = info.draftConfig();
        draft.setCooldownMinutes(99);
        service.updateDraft("test_strat", draft);

        info = service.getStrategy("test_strat");
        assertEquals(99, info.draftConfig().getCooldownMinutes());
    }

    @Test
    void testPromoteDraft() throws IOException {
        service.createDraft("test_strat");
        StrategyYamlConfig draft = service.getStrategy("test_strat").draftConfig();
        draft.setCooldownMinutes(42);
        service.updateDraft("test_strat", draft);

        service.promoteDraft("test_strat");

        StrategyVersionInfo info = service.getStrategy("test_strat");
        assertEquals(2, info.activeVersion());
        assertEquals(42, info.config().getCooldownMinutes());
        assertEquals("ACTIVE", info.status());
        assertNull(info.draftConfig());
        assertEquals(2, info.history().size());
    }
}
