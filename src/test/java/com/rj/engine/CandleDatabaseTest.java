package com.rj.engine;

import com.rj.model.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CandleDatabaseTest {

    @TempDir
    Path tempDir;

    private CandleDatabase db;
    private static final String SYMBOL = "NSE:SBIN-EQ";
    private static final LocalDate DATE = LocalDate.of(2026, 3, 25);

    @BeforeEach
    void setup() {
        db = new CandleDatabase(tempDir);
    }

    private List<Candle> sampleCandles() {
        return List.of(
                Candle.of(1711340100L, 100.0, 101.0, 99.5, 100.5, 1000),
                Candle.of(1711340160L, 100.5, 102.0, 100.0, 101.5, 1200),
                Candle.of(1711340220L, 101.5, 103.0, 101.0, 102.0, 800)
        );
    }

    @Test
    void storeAndLoadRoundTrip() {
        db.store(SYMBOL, DATE, sampleCandles());

        List<Candle> loaded = db.load(SYMBOL, DATE);
        assertEquals(3, loaded.size());
        assertEquals(1711340100L, loaded.get(0).timestamp);
        assertEquals(100.0, loaded.get(0).open, 0.01);
        assertEquals(101.0, loaded.get(0).high, 0.01);
        assertEquals(99.5, loaded.get(0).low, 0.01);
        assertEquals(100.5, loaded.get(0).close, 0.01);
        assertEquals(1000, loaded.get(0).volume);
    }

    @Test
    void loadNonexistentReturnsEmpty() {
        assertTrue(db.load(SYMBOL, DATE).isEmpty());
    }

    @Test
    void existsReturnsTrueAfterStore() {
        assertFalse(db.exists(SYMBOL, DATE));
        db.store(SYMBOL, DATE, sampleCandles());
        assertTrue(db.exists(SYMBOL, DATE));
    }

    @Test
    void availableDatesReturnsSorted() {
        LocalDate d1 = LocalDate.of(2026, 3, 24);
        LocalDate d2 = LocalDate.of(2026, 3, 25);
        LocalDate d3 = LocalDate.of(2026, 3, 26);

        db.store(SYMBOL, d3, sampleCandles());
        db.store(SYMBOL, d1, sampleCandles());
        db.store(SYMBOL, d2, sampleCandles());

        List<LocalDate> dates = db.availableDates(SYMBOL);
        assertEquals(3, dates.size());
        assertEquals(d1, dates.get(0));
        assertEquals(d2, dates.get(1));
        assertEquals(d3, dates.get(2));
    }

    @Test
    void availableSymbolsReturnsStoredSymbols() {
        db.store("NSE:SBIN-EQ", DATE, sampleCandles());
        db.store("NSE:RELIANCE-EQ", DATE, sampleCandles());

        Set<String> symbols = db.availableSymbols();
        assertEquals(2, symbols.size());
        assertTrue(symbols.contains("NSE:SBIN-EQ"));
        assertTrue(symbols.contains("NSE:RELIANCE-EQ"));
    }

    @Test
    void loadRangeAcrossMultipleDays() {
        LocalDate d1 = LocalDate.of(2026, 3, 24);
        LocalDate d2 = LocalDate.of(2026, 3, 25);

        db.store(SYMBOL, d1, sampleCandles());
        db.store(SYMBOL, d2, sampleCandles());

        List<Candle> all = db.loadRange(SYMBOL, d1, d2);
        assertEquals(6, all.size()); // 3 + 3
    }

    @Test
    void safeSymbolEncoding() {
        assertEquals("NSE_SBIN-EQ", CandleDatabase.safeSymbol("NSE:SBIN-EQ"));
        assertEquals("MCX_CRUDEOIL26MARFUT", CandleDatabase.safeSymbol("MCX:CRUDEOIL26MARFUT"));
    }

    @Test
    void storeEmptyListDoesNothing() {
        db.store(SYMBOL, DATE, List.of());
        assertFalse(db.exists(SYMBOL, DATE));
    }

    @Test
    void overwriteExistingFile() {
        db.store(SYMBOL, DATE, sampleCandles());
        assertEquals(3, db.load(SYMBOL, DATE).size());

        // Store with different data
        db.store(SYMBOL, DATE, List.of(Candle.of(1711340100L, 200.0, 201.0, 199.0, 200.5, 5000)));
        assertEquals(1, db.load(SYMBOL, DATE).size());
        assertEquals(200.0, db.load(SYMBOL, DATE).get(0).open, 0.01);
    }
}
