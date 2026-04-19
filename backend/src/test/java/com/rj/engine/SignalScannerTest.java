package com.rj.engine;

import com.rj.model.*;
import com.rj.strategy.ITradeStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SignalScannerTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static Candle bar(int hh, int mm, double price) {
        long epoch = ZonedDateTime.of(LocalDate.of(2026, 4, 1), LocalTime.of(hh, mm), IST)
                .toEpochSecond();
        return Candle.of(epoch, price, price + 1, price - 1, price, 10);
    }

    @Test
    void scan_emitsEventsWhenStrategyFires() {
        List<Candle> m1 = new ArrayList<>();
        // 9:15 to 10:44 → 90 M1 bars → 18 closed M5 bars
        for (int h = 9; h <= 10; h++) {
            int startMin = (h == 9) ? 15 : 0;
            int endMin = (h == 10) ? 45 : 60;
            for (int m = startMin; m < endMin; m++) m1.add(bar(h, m, 100.0));
        }

        TradeSignal fired = TradeSignal.builder()
                .symbol("NSE:SBIN-EQ").correlationId("x").direction(Signal.BUY)
                .confidence(0.9).strategyId("test-strat")
                .suggestedEntry(100).suggestedStopLoss(99).suggestedTarget(102)
                .build();

        ITradeStrategy strategy = Mockito.mock(ITradeStrategy.class);
        when(strategy.getId()).thenReturn("test-strat");
        when(strategy.evaluate(anyString(), any())).thenReturn(Optional.of(fired));

        SignalScanner scanner = new SignalScanner("NSE:SBIN-EQ", Timeframe.M5, strategy);
        List<SignalScanner.SignalEvent> events = scanner.scan(m1, LocalDate.of(2026, 4, 1));

        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(e -> assertThat(e.signal().getDirection()).isEqualTo(Signal.BUY));
        assertThat(events).allSatisfy(e -> assertThat(e.tradingDate()).isEqualTo(LocalDate.of(2026, 4, 1)));
    }

    @Test
    void scan_emitsNothingWhenStrategyReturnsEmpty() {
        ITradeStrategy strategy = Mockito.mock(ITradeStrategy.class);
        when(strategy.evaluate(anyString(), any())).thenReturn(Optional.empty());

        List<Candle> m1 = new ArrayList<>();
        for (int m = 15; m < 25; m++) m1.add(bar(9, m, 100));

        SignalScanner scanner = new SignalScanner("NSE:SBIN-EQ", Timeframe.M5, strategy);
        assertThat(scanner.scan(m1, LocalDate.of(2026, 4, 1))).isEmpty();
    }

    @Test
    void scan_emptyInput_returnsEmpty() {
        ITradeStrategy strategy = Mockito.mock(ITradeStrategy.class);
        SignalScanner scanner = new SignalScanner("NSE:SBIN-EQ", Timeframe.M5, strategy);
        assertThat(scanner.scan(List.of(), LocalDate.of(2026, 4, 1))).isEmpty();
    }
}
