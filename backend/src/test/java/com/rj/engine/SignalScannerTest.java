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

    @Test
    void scan_primaryEqualsM15_noDoubleFeed() {
        // 45 minutes of M1 → 3 M15 bars when aligned to :15/:30/:45 boundaries
        List<Candle> m1 = new ArrayList<>();
        for (int m = 15; m < 60; m++) m1.add(bar(9, m, 100));

        ITradeStrategy strategy = Mockito.mock(ITradeStrategy.class);
        when(strategy.evaluate(anyString(), any())).thenReturn(Optional.empty());

        SignalScanner scanner = new SignalScanner("NSE:SBIN-EQ", Timeframe.M15, strategy);
        scanner.scan(m1, LocalDate.of(2026, 4, 1));

        // With 3 M15 bars, strategy.evaluate should be called exactly 3 times
        // (once per primary bar). The double-feed bug would cause additional
        // invocations or corrupted state (not directly observable here, but the
        // call-count check rules out the obvious path of counting M15 bars in
        // both the advance loop and the primary feed).
        Mockito.verify(strategy, Mockito.times(3)).evaluate(anyString(), any());
    }

    @Test
    void scan_primaryEqualsH1_noDoubleFeed() {
        // 120 minutes of M1 from 09:30 (inside one H1 bucket) to 11:30
        // The H1 truncation drops the 8:30-IST bucket (below NSE session); 09:30-10:29 and 10:30-11:29 are full H1 bars.
        List<Candle> m1 = new ArrayList<>();
        for (int m = 30; m < 60; m++) m1.add(bar(9, m, 100));
        for (int m = 0; m < 60; m++) m1.add(bar(10, m, 100));
        for (int m = 0; m < 30; m++) m1.add(bar(11, m, 100));

        ITradeStrategy strategy = Mockito.mock(ITradeStrategy.class);
        when(strategy.evaluate(anyString(), any())).thenReturn(Optional.empty());

        SignalScanner scanner = new SignalScanner("NSE:SBIN-EQ", Timeframe.H1, strategy);
        scanner.scan(m1, LocalDate.of(2026, 4, 1));

        // Two closed H1 bars → strategy.evaluate called exactly 2 times.
        Mockito.verify(strategy, Mockito.times(2)).evaluate(anyString(), any());
    }
}
