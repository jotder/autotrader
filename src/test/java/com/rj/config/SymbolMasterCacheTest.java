package com.rj.config;

import com.rj.model.dim.SymbolMasterEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SymbolMasterCacheTest {

    private static SymbolMasterCache cache;

    @BeforeAll
    static void loadCache() {
        cache = SymbolMasterCache.load(Path.of("src/test/resources/symbol_master"));
    }

    @Test
    void loadsTotalSymbols() {
        // 3 from NSE_CM + 2 from NSE_FO = 5
        assertEquals(5, cache.size());
    }

    @Test
    void lookupByTicker() {
        Optional<SymbolMasterEntry> sbin = cache.byTicker("NSE:SBIN-EQ");
        assertTrue(sbin.isPresent());
        assertEquals("SBIN", sbin.get().underlyingSymbol());
        assertEquals(10, sbin.get().exchange());
        assertEquals(10, sbin.get().segment());
        assertEquals(0.05, sbin.get().tickSize());
    }

    @Test
    void lookupByTickerNotFound() {
        assertTrue(cache.byTicker("NSE:UNKNOWN-EQ").isEmpty());
    }

    @Test
    void lookupByUnderlying() {
        List<SymbolMasterEntry> nifty = cache.byUnderlying("NIFTY");
        // NIFTY50-INDEX (CM) + NIFTY26MARFUT (FO)
        assertEquals(2, nifty.size());
    }

    @Test
    void lookupByExchangeSegment() {
        // NSE CM = exchange 10, segment 10 → 3 entries
        List<SymbolMasterEntry> nseCm = cache.byExchangeSegment(10, 10);
        assertEquals(3, nseCm.size());

        // NSE FO = exchange 10, segment 11 → 2 entries
        List<SymbolMasterEntry> nseFo = cache.byExchangeSegment(10, 11);
        assertEquals(2, nseFo.size());
    }

    @Test
    void allTickersReturnsAllSymbols() {
        assertTrue(cache.allTickers().contains("NSE:SBIN-EQ"));
        assertTrue(cache.allTickers().contains("NSE:NIFTY26MARFUT"));
    }

    @Test
    void allUnderlyingsReturnsDistinct() {
        assertTrue(cache.allUnderlyings().contains("SBIN"));
        assertTrue(cache.allUnderlyings().contains("NIFTY"));
    }

    @Test
    void searchByPartialTicker() {
        List<SymbolMasterEntry> results = cache.search("SBIN", 10);
        assertEquals(1, results.size());
        assertEquals("NSE:SBIN-EQ", results.get(0).symbolTicker());
    }

    @Test
    void searchByPartialName() {
        List<SymbolMasterEntry> results = cache.search("RELIANCE", 10);
        assertEquals(1, results.size());
    }

    @Test
    void searchRespectsLimit() {
        List<SymbolMasterEntry> results = cache.search("NSE", 2);
        assertEquals(2, results.size());
    }

    @Test
    void searchEmptyQueryReturnsEmpty() {
        assertTrue(cache.search("", 10).isEmpty());
        assertTrue(cache.search(null, 10).isEmpty());
    }

    @Test
    void futuresHaveCorrectSegment() {
        Optional<SymbolMasterEntry> fut = cache.byTicker("NSE:NIFTY26MARFUT");
        assertTrue(fut.isPresent());
        assertEquals(11, fut.get().segment());  // FO segment
        assertEquals(11, fut.get().exInstType()); // FUTIDX
    }

    @Test
    void missingDirectoryReturnsEmptyCache() {
        SymbolMasterCache empty = SymbolMasterCache.load(Path.of("nonexistent/dir"));
        assertEquals(0, empty.size());
    }
}
