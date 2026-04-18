package com.rj.engine;

import com.rj.model.Candle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DownloadTrackerTest {

    @TempDir
    Path tempDir;

    @Test
    void startJobReturnsJobId() {
        var tracker = createTracker();
        String jobId = tracker.startJob(List.of("NSE:SBIN-EQ"), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5));
        assertNotNull(jobId);
        assertFalse(jobId.isEmpty());
    }

    @Test
    void getJobReturnsStatus() throws InterruptedException {
        var tracker = createTracker();
        String jobId = tracker.startJob(List.of("NSE:SBIN-EQ"), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1));

        // Wait for job to complete (uses mock downloader, should be fast)
        Thread.sleep(500);

        var job = tracker.getJob(jobId);
        assertNotNull(job);
        assertEquals("COMPLETED", job.status);
        assertEquals(1, job.symbolsCompleted);
    }

    @Test
    void getJobNotFound() {
        var tracker = createTracker();
        assertNull(tracker.getJob("nonexistent"));
    }

    @Test
    void jobMapContainsFields() throws InterruptedException {
        var tracker = createTracker();
        String jobId = tracker.startJob(
                List.of("NSE:SBIN-EQ", "NSE:RELIANCE-EQ"),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5));

        Thread.sleep(1000);

        var job = tracker.getJob(jobId);
        var map = job.toMap();
        assertEquals(jobId, map.get("jobId"));
        assertNotNull(map.get("status"));
        assertNotNull(map.get("progress"));
        assertNotNull(map.get("startTime"));
    }

    @Test
    void allJobsReturnsList() {
        var tracker = createTracker();
        tracker.startJob(List.of("NSE:SBIN-EQ"), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1));
        tracker.startJob(List.of("NSE:RELIANCE-EQ"), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1));
        assertEquals(2, tracker.allJobs().size());
    }

    private DownloadTracker createTracker() {
        // Mock downloader that does nothing (no real API calls)
        var db = new CandleDatabase(tempDir);
        var downloader = new CandleDownloader(null, db) {
            @Override
            public int download(String symbol, LocalDate from, LocalDate to) {
                // Simulate: return 0 days (no data to download in test)
                return 0;
            }
        };
        return new DownloadTracker(downloader);
    }
}
