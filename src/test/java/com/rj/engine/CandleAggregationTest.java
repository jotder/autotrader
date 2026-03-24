package com.rj.engine;

import com.rj.model.Candle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandleAggregationTest {

    @Test
    void aggregateM1toM5() {
        // Create 10 M1 candles (2 groups of 5, at 60-second intervals)
        // Group 1: timestamp 0..240 (boundary 0)
        // Group 2: timestamp 300..540 (boundary 300)
        var m1 = new ArrayList<Candle>();
        long base = 1711339800L; // aligned to 5-minute boundary
        for (int i = 0; i < 10; i++) {
            double price = 100 + i;
            m1.add(Candle.of(base + i * 60, price, price + 1, price - 0.5, price + 0.5, 100 + i));
        }

        List<Candle> m5 = BacktestEngine.aggregateToHigherTimeframe(m1, 5);
        assertEquals(2, m5.size());

        // First M5 candle: aggregated from first 5 M1 candles
        Candle first = m5.get(0);
        assertEquals(base, first.timestamp); // timestamp of first bar in group
        assertEquals(100.0, first.open, 0.01);     // first bar's open
        assertEquals(104.5, first.close, 0.01);     // last bar's close (bar index 4: 104+0.5)
        assertEquals(105.0, first.high, 0.01);      // max high (104+1)
        assertEquals(99.5, first.low, 0.01);         // min low (100-0.5)
        assertEquals(510, first.volume);              // 100+101+102+103+104

        // Second M5 candle: aggregated from next 5 M1 candles
        Candle second = m5.get(1);
        assertEquals(base + 300, second.timestamp);
        assertEquals(105.0, second.open, 0.01);
        assertEquals(109.5, second.close, 0.01);
    }

    @Test
    void aggregateEmptyReturnsEmpty() {
        assertTrue(BacktestEngine.aggregateToHigherTimeframe(List.of(), 5).isEmpty());
        assertTrue(BacktestEngine.aggregateToHigherTimeframe(null, 5).isEmpty());
    }

    @Test
    void aggregateSingleCandle() {
        List<Candle> m1 = List.of(Candle.of(1711339800L, 100, 101, 99, 100.5, 500));
        List<Candle> m5 = BacktestEngine.aggregateToHigherTimeframe(m1, 5);
        assertEquals(1, m5.size());
        assertEquals(100, m5.get(0).open, 0.01);
    }

    @Test
    void aggregateM1toM15() {
        // 15 M1 candles should produce 1 M15 candle
        var m1 = new ArrayList<Candle>();
        long base = 1711339200L; // aligned to 15-minute boundary (divisible by 900)
        for (int i = 0; i < 15; i++) {
            m1.add(Candle.of(base + i * 60, 100 + i, 100 + i + 1, 100 + i - 0.5, 100 + i + 0.5, 100));
        }

        List<Candle> m15 = BacktestEngine.aggregateToHigherTimeframe(m1, 15);
        assertEquals(1, m15.size());
        assertEquals(100.0, m15.get(0).open, 0.01);
        assertEquals(114.5, m15.get(0).close, 0.01);
        assertEquals(115.0, m15.get(0).high, 0.01);
        assertEquals(99.5, m15.get(0).low, 0.01);
        assertEquals(1500, m15.get(0).volume);
    }
}
