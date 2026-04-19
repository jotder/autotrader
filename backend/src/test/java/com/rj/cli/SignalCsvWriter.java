package com.rj.cli;

import com.rj.engine.SignalScanner;
import com.rj.model.Timeframe;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class SignalCsvWriter implements AutoCloseable {

    private static final String HEADER =
            "symbol,date,bar_timestamp,timeframe,side,entry,sl,tp,confidence,reason";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final BufferedWriter writer;
    private final Timeframe timeframe;

    private SignalCsvWriter(BufferedWriter w, Timeframe tf) {
        this.writer = w;
        this.timeframe = tf;
    }

    /** Open a new CSV under {@code data/signals/run-<timestamp>.csv}. */
    public static SignalCsvWriter openDefault(Timeframe tf) {
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(LocalDateTime.now(IST));
        Path dir = Path.of("data", "signals");
        try {
            Files.createDirectories(dir);
            return open(dir.resolve("run-" + ts + ".csv"), tf);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SignalCsvWriter open(Path path, Timeframe tf) throws IOException {
        BufferedWriter w = Files.newBufferedWriter(path);
        w.write(HEADER);
        w.newLine();
        w.flush();
        return new SignalCsvWriter(w, tf);
    }

    public void write(SignalScanner.SignalEvent ev) {
        var s = ev.signal();
        String reason = s.getReason() == null ? "" : s.getReason().replace(",", ";");
        String row = String.join(",",
                s.getSymbol(),
                ev.tradingDate().toString(),
                ev.barTimestamp().toString(),
                timeframe.name(),
                s.getDirection().name(),
                Double.toString(s.getSuggestedEntry()),
                Double.toString(s.getSuggestedStopLoss()),
                Double.toString(s.getSuggestedTarget()),
                Double.toString(s.getConfidence()),
                reason);
        try {
            writer.write(row);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override public void close() throws IOException { writer.close(); }
}
