package com.rj.cli;

import com.rj.engine.SignalScanner;
import com.rj.model.Signal;
import com.rj.model.Timeframe;
import com.rj.model.TradeSignal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignalCsvWriterTest {

    @Test
    void write_headerAndRow_written(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("signals.csv");
        TradeSignal sig = TradeSignal.builder()
                .symbol("NSE:SBIN-EQ").correlationId("x").direction(Signal.BUY)
                .confidence(0.9).strategyId("trend_following")
                .suggestedEntry(100).suggestedStopLoss(99).suggestedTarget(102)
                .reason("bullish break").build();
        var event = new SignalScanner.SignalEvent(sig,
                ZonedDateTime.of(2026, 4, 1, 9, 20, 0, 0, ZoneId.of("Asia/Kolkata")),
                LocalDate.of(2026, 4, 1));

        try (var w = SignalCsvWriter.open(out, Timeframe.M5)) {
            w.write(event);
        }

        List<String> lines = Files.readAllLines(out);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo(
                "symbol,date,bar_timestamp,timeframe,side,entry,sl,tp,confidence,reason");
        assertThat(lines.get(1))
                .contains("NSE:SBIN-EQ")
                .contains("2026-04-01")
                .contains("M5")
                .contains("BUY")
                .contains("100.0")
                .contains("99.0")
                .contains("102.0")
                .contains("0.9")
                .contains("bullish break");
    }

    @Test
    void write_reasonWithCommas_isEscapedOrStripped(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("signals.csv");
        TradeSignal sig = TradeSignal.builder()
                .symbol("X").correlationId("x").direction(Signal.SELL)
                .confidence(0.5).strategyId("s")
                .suggestedEntry(10).suggestedStopLoss(11).suggestedTarget(9)
                .reason("a,b,c").build();
        var event = new SignalScanner.SignalEvent(sig,
                ZonedDateTime.of(2026, 4, 1, 9, 20, 0, 0, ZoneId.of("Asia/Kolkata")),
                LocalDate.of(2026, 4, 1));

        try (var w = SignalCsvWriter.open(out, Timeframe.M5)) {
            w.write(event);
        }

        // The row should still have exactly 10 columns after comma-stripping
        List<String> lines = Files.readAllLines(out);
        long commas = lines.get(1).chars().filter(c -> c == ',').count();
        assertThat(commas).isEqualTo(9);   // 10 columns → 9 separators
    }
}
