package com.rj.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks async M1 candle download jobs launched via REST API.
 * Jobs run on virtual threads and report progress in-memory.
 */
public class DownloadTracker {

    private static final Logger log = LoggerFactory.getLogger(DownloadTracker.class);

    private final CandleDownloader downloader;
    private final ConcurrentHashMap<String, DownloadJob> jobs = new ConcurrentHashMap<>();

    public DownloadTracker(CandleDownloader downloader) {
        this.downloader = downloader;
    }

    /**
     * Start an async download job. Returns immediately with a job ID.
     */
    public String startJob(List<String> symbols, LocalDate from, LocalDate to) {
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        DownloadJob job = new DownloadJob(jobId, symbols, from, to);
        jobs.put(jobId, job);

        Thread.ofVirtual().name("download-" + jobId).start(() -> {
            try {
                job.status = "RUNNING";
                for (String symbol : symbols) {
                    if (job.status.equals("FAILED")) break;
                    try {
                        int days = downloader.download(symbol, from, to);
                        job.results.put(symbol, days);
                    } catch (Exception e) {
                        log.warn("Download failed for {}: {}", symbol, e.getMessage());
                        job.results.put(symbol, -1);
                    }
                    job.symbolsCompleted++;
                }
                job.status = "COMPLETED";
                job.endTime = Instant.now();
                log.info("Download job {} completed: {}", jobId, job.results);
            } catch (Exception e) {
                job.status = "FAILED";
                job.error = e.getMessage();
                job.endTime = Instant.now();
                log.error("Download job {} failed: {}", jobId, e.getMessage(), e);
            }
        });

        return jobId;
    }

    public DownloadJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    public Collection<DownloadJob> allJobs() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    /**
     * Represents a download job's state.
     */
    public static class DownloadJob {
        public final String jobId;
        public final List<String> symbols;
        public final LocalDate from;
        public final LocalDate to;
        public final Instant startTime = Instant.now();
        public volatile String status = "PENDING";
        public volatile int symbolsCompleted = 0;
        public volatile Instant endTime;
        public volatile String error;
        public final Map<String, Integer> results = new LinkedHashMap<>();

        DownloadJob(String jobId, List<String> symbols, LocalDate from, LocalDate to) {
            this.jobId = jobId;
            this.symbols = List.copyOf(symbols);
            this.from = from;
            this.to = to;
        }

        public Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("jobId", jobId);
            map.put("status", status);
            map.put("symbols", symbols);
            map.put("from", from.toString());
            map.put("to", to.toString());
            map.put("progress", symbolsCompleted + "/" + symbols.size());
            map.put("results", results);
            map.put("startTime", startTime.toString());
            map.put("endTime", endTime != null ? endTime.toString() : null);
            if (error != null) map.put("error", error);
            return map;
        }
    }
}
