package com.rj.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SymbolRegistryTest {

    private static final Path TEST_SYMBOLS = Path.of("src/test/resources/test-symbols.yaml");

    // ── Happy path ──────────────────────────────────────────────────────────────

    @Test
    void loadValidYaml_returnsCorrectTotalCount() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        assertEquals(6, reg.size());
    }

    @Test
    void loadValidYaml_allSymbolsContainsEverySymbol() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        String[] all = reg.allSymbols();
        assertEquals(6, all.length);
        Set<String> set = Set.of(all);
        assertTrue(set.contains("NSE:SBIN-EQ"));
        assertTrue(set.contains("NSE:RELIANCE-EQ"));
        assertTrue(set.contains("NSE:NIFTY50-INDEX"));
        assertTrue(set.contains("NSE:NIFTY26MARFUT"));
        assertTrue(set.contains("MCX:CRUDEOIL26MARFUT"));
        assertTrue(set.contains("MCX:GOLDM26MARFUT"));
    }

    @Test
    void symbolsForCM_returnsOnlyCapitalMarketSymbols() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        List<String> cm = reg.symbolsFor(MarketCategory.CM);
        assertEquals(3, cm.size());
        assertTrue(cm.contains("NSE:SBIN-EQ"));
        assertTrue(cm.contains("NSE:RELIANCE-EQ"));
        assertTrue(cm.contains("NSE:NIFTY50-INDEX"));
    }

    @Test
    void symbolsForFO_returnsOnlyDerivatives() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        List<String> fo = reg.symbolsFor(MarketCategory.FO);
        assertEquals(1, fo.size());
        assertEquals("NSE:NIFTY26MARFUT", fo.get(0));
    }

    @Test
    void symbolsForCOM_returnsOnlyCommodities() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        List<String> com = reg.symbolsFor(MarketCategory.COM);
        assertEquals(2, com.size());
        assertTrue(com.contains("MCX:CRUDEOIL26MARFUT"));
        assertTrue(com.contains("MCX:GOLDM26MARFUT"));
    }

    @Test
    void contains_returnsTrueForRegisteredSymbol() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        assertTrue(reg.contains("NSE:SBIN-EQ"));
        assertTrue(reg.contains("MCX:GOLDM26MARFUT"));
    }

    @Test
    void contains_returnsFalseForUnknownSymbol() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        assertFalse(reg.contains("NSE:FAKE-EQ"));
        assertFalse(reg.contains(""));
    }

    @Test
    void allSymbolSet_returnsUnmodifiableSet() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        Set<String> set = reg.allSymbolSet();
        assertThrows(UnsupportedOperationException.class, () -> set.add("NSE:HACK-EQ"));
    }

    @Test
    void symbolsForCategory_returnsUnmodifiableList() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        List<String> cm = reg.symbolsFor(MarketCategory.CM);
        assertThrows(UnsupportedOperationException.class, () -> cm.add("NSE:HACK-EQ"));
    }

    // ── Error cases ─────────────────────────────────────────────────────────────

    @Test
    void loadMissingFile_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> SymbolRegistry.load(Path.of("nonexistent/symbols.yaml")));
    }

    @Test
    void loadEmptyFile_throwsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path empty = tempDir.resolve("empty.yaml");
        Files.writeString(empty, "");
        assertThrows(IllegalArgumentException.class, () -> SymbolRegistry.load(empty));
    }

    @Test
    void loadNoSymbolsKey_throwsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path bad = tempDir.resolve("bad.yaml");
        Files.writeString(bad, "other_key:\n  - foo\n");
        assertThrows(IllegalArgumentException.class, () -> SymbolRegistry.load(bad));
    }

    @Test
    void loadUnknownCategoryKey_throwsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path bad = tempDir.resolve("bad-cat.yaml");
        Files.writeString(bad, "symbols:\n  crypto:\n    - BTC:BTCUSD\n");
        assertThrows(IllegalArgumentException.class, () -> SymbolRegistry.load(bad));
    }

    // ── Duplicate handling ──────────────────────────────────────────────────────

    @Test
    void duplicateSymbolAcrossCategories_deduplicatesAndKeepsFirst(@TempDir Path tempDir) throws IOException {
        Path dup = tempDir.resolve("dup.yaml");
        Files.writeString(dup, """
                symbols:
                  cm:
                    - NSE:SBIN-EQ
                  fo:
                    - NSE:SBIN-EQ
                    - NSE:NIFTY26MARFUT
                """);
        SymbolRegistry reg = SymbolRegistry.load(dup);
        // Total should be 2 (deduplicated), not 3
        assertEquals(2, reg.size());
        // SBIN-EQ stays in CM (first occurrence)
        assertTrue(reg.symbolsFor(MarketCategory.CM).contains("NSE:SBIN-EQ"));
        // FO should only have NIFTY26MARFUT (SBIN-EQ removed as duplicate)
        assertEquals(1, reg.symbolsFor(MarketCategory.FO).size());
        assertEquals("NSE:NIFTY26MARFUT", reg.symbolsFor(MarketCategory.FO).get(0));
    }

    // ── Strategy symbol validation ──────────────────────────────────────────────

    @Test
    void validateStrategySymbols_allValid_returnsEmptyList() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        List<String> invalid = reg.validateStrategySymbols(
                "trend_following", List.of("NSE:SBIN-EQ", "NSE:RELIANCE-EQ"));
        assertTrue(invalid.isEmpty());
    }

    @Test
    void validateStrategySymbols_someInvalid_returnsInvalidOnes() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        List<String> invalid = reg.validateStrategySymbols(
                "trend_following", List.of("NSE:SBIN-EQ", "NSE:FAKE-EQ", "BSE:GHOST-EQ"));
        assertEquals(2, invalid.size());
        assertTrue(invalid.contains("NSE:FAKE-EQ"));
        assertTrue(invalid.contains("BSE:GHOST-EQ"));
    }

    @Test
    void validateStrategySymbols_emptyList_returnsEmptyList() {
        SymbolRegistry reg = SymbolRegistry.load(TEST_SYMBOLS);
        List<String> invalid = reg.validateStrategySymbols("empty_strat", List.of());
        assertTrue(invalid.isEmpty());
    }

    // ── Production file ─────────────────────────────────────────────────────────

    @Test
    void loadProductionSymbolsYaml_ifPresent() {
        Path prodPath = Path.of("config/symbols.yaml");
        if (Files.exists(prodPath)) {
            SymbolRegistry reg = SymbolRegistry.load(prodPath);
            assertTrue(reg.size() > 0, "Production symbols.yaml should have at least 1 symbol");
            // Verify known symbols from intraday.yaml are present
            assertTrue(reg.contains("NSE:SBIN-EQ"), "trend_following symbol missing");
            assertTrue(reg.contains("NSE:NIFTY50-INDEX"), "mean_reversion symbol missing");
        }
    }
}
