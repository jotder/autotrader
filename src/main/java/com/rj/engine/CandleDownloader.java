package com.rj.engine;

import com.rj.model.Candle;
import com.tts.in.model.StockHistoryModel;
import fyers.FyersDataApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Downloads M1 historical candles from Fyers REST API and stores them
 * in the {@link CandleDatabase}.
 * <p>
 * Rate-limit aware — configurable delay between API calls.
 * Skips dates that already exist in the database.
 */
public class CandleDownloader {

    private static final Logger log = LoggerFactory.getLogger(CandleDownloader.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final FyersDataApi dataApi;
    private final CandleDatabase db;
    private final long delayBetweenCallsMs;
    private final BrokerCircuitBreaker circuitBreaker; // nullable

    public CandleDownloader(FyersDataApi dataApi, CandleDatabase db) {
        this(dataApi, db, 500, null);
    }

    public CandleDownloader(FyersDataApi dataApi, CandleDatabase db, long delayBetweenCallsMs,
                            BrokerCircuitBreaker circuitBreaker) {
        this.dataApi = dataApi;
        this.db = db;
        this.delayBetweenCallsMs = delayBetweenCallsMs;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Download and store M1 candles for one symbol over a date range.
     * Skips dates already in the database.
     *
     * @return count of days successfully downloaded
     */
    public int download(String symbol, LocalDate from, LocalDate to) {
        int downloaded = 0;

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (db.exists(symbol, date)) {
                log.debug("Skipping {} on {} — already in DB", symbol, date);
                continue;
            }

            try {
                List<Candle> candles = fetchDay(symbol, date);
                if (candles != null && !candles.isEmpty()) {
                    db.store(symbol, date, candles);
                    downloaded++;
                    log.info("Downloaded {} M1 candles for {} on {}", candles.size(), symbol, date);
                } else {
                    log.debug("No data for {} on {} (holiday/weekend?)", symbol, date);
                }

                if (delayBetweenCallsMs > 0) {
                    Thread.sleep(delayBetweenCallsMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Download interrupted for {} at {}", symbol, date);
                break;
            } catch (Exception e) {
                log.warn("Failed to download {} on {}: {}", symbol, date, e.getMessage());
            }
        }

        return downloaded;
    }

    /**
     * Download for multiple symbols.
     *
     * @return map of symbol → days downloaded
     */
    public Map<String, Integer> downloadBatch(List<String> symbols, LocalDate from, LocalDate to) {
        var results = new LinkedHashMap<String, Integer>();
        for (String symbol : symbols) {
            results.put(symbol, download(symbol, from, to));
        }
        return results;
    }

    // ── Fyers API call ──────────────────────────────────────────────────────

    private List<Candle> fetchDay(String symbol, LocalDate date) {
        // Convert date to epoch range (start of day to end of day IST)
        ZonedDateTime startOfDay = date.atStartOfDay(IST);
        ZonedDateTime endOfDay = date.atTime(23, 59, 59).atZone(IST);

        StockHistoryModel model = new StockHistoryModel();
        model.Symbol = symbol;
        model.Resolution = "1";  // M1 resolution
        model.DateFormat = "0";  // epoch format
        model.RangeFrom = String.valueOf(startOfDay.toEpochSecond());
        model.RangeTo = String.valueOf(endOfDay.toEpochSecond());
        model.ContFlag = 0;

        if (circuitBreaker != null) {
            return circuitBreaker.execute(() -> dataApi.getStockHistory(model), false);
        }
        return dataApi.getStockHistory(model);
    }
}
