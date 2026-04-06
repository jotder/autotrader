package com.rj.web;

import com.rj.engine.CandleDatabase;
import com.rj.engine.DownloadTracker;
import com.rj.engine.SymbolProfiler;
import com.rj.model.Candle;
import com.rj.model.SymbolProfile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class CandleController {

    private final CandleDatabase candleDatabase;
    private final DownloadTracker downloadTracker;
    private final SymbolProfiler symbolProfiler;

    public CandleController(CandleDatabase candleDatabase,
                            DownloadTracker downloadTracker,
                            SymbolProfiler symbolProfiler) {
        this.candleDatabase = candleDatabase;
        this.downloadTracker = downloadTracker;
        this.symbolProfiler = symbolProfiler;
    }

    // ── Candle Database endpoints ─────────────────────────────────────────

    @GetMapping("/candle-db/symbols")
    public Set<String> candleDbSymbols() {
        return candleDatabase.availableSymbols();
    }

    @GetMapping("/candle-db/summary")
    public List<Map<String, Object>> candleDbSummary() {
        var symbols = candleDatabase.availableSymbols();
        var result = new ArrayList<Map<String, Object>>();
        for (String s : symbols) {
            var dates = candleDatabase.availableDates(s);
            if (!dates.isEmpty()) {
                result.add(Map.of(
                        "symbol", s,
                        "startDate", dates.getFirst().toString(),
                        "endDate", dates.getLast().toString(),
                        "count", dates.size()
                ));
            }
        }
        return result;
    }

    @GetMapping("/candle-db/{symbol}/dates")
    public List<LocalDate> candleDbDates(@PathVariable String symbol) {
        return candleDatabase.availableDates(symbol);
    }

    @GetMapping("/candle-db/{symbol}")
    public ResponseEntity<?> candleDbLoad(@PathVariable String symbol,
                                          @RequestParam String date) {
        LocalDate d = LocalDate.parse(date);
        List<Candle> candles = candleDatabase.load(symbol, d);
        if (candles.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "symbol", symbol,
                "date", date,
                "count", candles.size(),
                "candles", candles));
    }

    // ── Candle download endpoints ────────────────────────────────────────

    @PostMapping("/candle-db/download")
    public ResponseEntity<?> candleDbDownload(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> symbols = (List<String>) request.get("symbols");
        String fromStr = (String) request.get("from");
        String toStr = (String) request.get("to");

        if (symbols == null || symbols.isEmpty() || fromStr == null || toStr == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Required: symbols (list), from (date), to (date)"));
        }

        LocalDate from = LocalDate.parse(fromStr);
        LocalDate to = LocalDate.parse(toStr);

        String jobId = downloadTracker.startJob(symbols, from, to);
        return ResponseEntity.accepted().body(Map.of(
                "jobId", jobId,
                "status", "RUNNING",
                "message", "Download started for " + symbols.size() + " symbols",
                "checkUrl", "/api/candle-db/download/" + jobId));
    }

    @GetMapping("/candle-db/download/{jobId}")
    public ResponseEntity<?> candleDbDownloadStatus(@PathVariable String jobId) {
        var job = downloadTracker.getJob(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job.toMap());
    }

    @GetMapping("/candle-db/downloads")
    public List<Map<String, Object>> candleDbDownloads() {
        return downloadTracker.allJobs().stream()
                .map(DownloadTracker.DownloadJob::toMap)
                .toList();
    }

    // ── Symbol Profile endpoint ───────────────────────────────────────────

    @GetMapping("/profile/{symbol}")
    public ResponseEntity<?> profile(@PathVariable String symbol,
                                     @RequestParam String from,
                                     @RequestParam String to) {
        SymbolProfile profile = symbolProfiler.profile(symbol, LocalDate.parse(from), LocalDate.parse(to));
        if (profile == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Insufficient data for profiling " + symbol));
        }
        return ResponseEntity.ok(profile);
    }
}
