package com.rj.config;

import com.rj.model.dim.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DimensionDataCacheTest {

    private static DimensionDataCache cache;

    @BeforeAll
    static void loadCache() {
        cache = DimensionDataCache.load(Path.of("src/test/resources/dim"));
    }

    @Test
    void loadsExchanges() {
        List<Exchange> exchanges = cache.exchanges();
        assertEquals(2, exchanges.size());
        assertEquals("NSE (National Stock Exchange)", exchanges.get(0).name());
        assertEquals(10, exchanges.get(0).code());
    }

    @Test
    void exchangeByCodeReturnsMatch() {
        Optional<Exchange> nse = cache.exchangeByCode(10);
        assertTrue(nse.isPresent());
        assertTrue(nse.get().name().contains("NSE"));
    }

    @Test
    void exchangeByCodeReturnsEmptyForUnknown() {
        assertTrue(cache.exchangeByCode(99).isEmpty());
    }

    @Test
    void loadsSegments() {
        assertEquals(2, cache.segments().size());
        assertTrue(cache.segmentByCode(10).isPresent());
        assertEquals("Capital Market", cache.segmentByCode(10).get().name());
    }

    @Test
    void loadsExchangeSegments() {
        List<ExchangeSegment> es = cache.exchangeSegments();
        assertEquals(3, es.size());
        assertTrue(cache.isValidExchangeSegment(10, 10));  // NSE CM
        assertTrue(cache.isValidExchangeSegment(10, 11));  // NSE FO
        assertFalse(cache.isValidExchangeSegment(12, 11)); // BSE FO not in test data
    }

    @Test
    void loadsInstrumentTypes() {
        List<InstrumentType> types = cache.instrumentTypes();
        assertEquals(3, types.size());
        // code 0 = EQUITY
        List<InstrumentType> equities = cache.instrumentTypesByCode(0);
        assertEquals(1, equities.size());
        assertEquals("EQUITY", equities.get(0).name());
    }

    @Test
    void loadsOrderSides() {
        assertEquals(2, cache.orderSides().size());
    }

    @Test
    void loadsOrderSources() {
        assertEquals(2, cache.orderSources().size());
    }

    @Test
    void loadsOrderStatuses() {
        assertEquals(2, cache.orderStatuses().size());
        assertTrue(cache.orderStatusByCode(2).isPresent());
        assertEquals("Traded / Filled", cache.orderStatusByCode(2).get().name());
    }

    @Test
    void loadsOrderTypes() {
        assertEquals(2, cache.orderTypes().size());
        assertTrue(cache.orderTypeByCode(1).isPresent());
        assertEquals("Limit order", cache.orderTypeByCode(1).get().name());
    }

    @Test
    void loadsPositionSides() {
        assertEquals(3, cache.positionSides().size());
    }

    @Test
    void loadsProductTypes() {
        assertEquals(2, cache.productTypes().size());
        assertTrue(cache.productTypeByCode("CNC").isPresent());
        assertEquals("For equity only", cache.productTypeByCode("CNC").get().name());
    }

    @Test
    void loadsHoldingTypes() {
        assertEquals(2, cache.holdingTypes().size());
    }

    @Test
    void allTablesReturnsAllDimensions() {
        var tables = cache.allTables();
        assertEquals(11, tables.size());
        assertTrue(tables.containsKey("exchanges"));
        assertTrue(tables.containsKey("instrumentTypes"));
    }

    @Test
    void tableByNameReturnsCorrectTable() {
        assertTrue(cache.tableByName("exchanges").isPresent());
        assertTrue(cache.tableByName("unknown").isEmpty());
    }

    @Test
    void missingDirectoryReturnsEmptyCache() {
        DimensionDataCache empty = DimensionDataCache.load(Path.of("nonexistent/dir"));
        assertEquals(0, empty.exchanges().size());
        assertEquals(0, empty.orderTypes().size());
    }
}
