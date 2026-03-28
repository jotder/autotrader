package com.rj.model;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Supported candle resolutions.
 * Each value carries its {@link Duration} and can truncate a ZonedDateTime
 * to the candle-boundary floor, and compute the delay until the next boundary.
 */
public enum Timeframe {

    M1(Duration.ofMinutes(1), "1m"),
    M5(Duration.ofMinutes(5), "5m"),
    M15(Duration.ofMinutes(15), "15m"),
    H1(Duration.ofHours(1), "1h");

    private final Duration duration;
    private final String label;

    Timeframe(Duration duration, String label) {
        this.duration = duration;
        this.label = label;
    }

    public Duration getDuration() {
        return duration;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Floor {@code dt} to the nearest lower candle-boundary for this timeframe.
     * Works by truncating epoch-seconds to the nearest multiple of the period.
     */
    public ZonedDateTime truncate(ZonedDateTime dt) {
        long epochSecs = dt.toEpochSecond();
        long periodSecs = duration.toSeconds();
        long floored = (epochSecs / periodSecs) * periodSecs;
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(floored), dt.getZone());
    }

    /**
     * Milliseconds from {@code now} until the next candle boundary.
     * Adds a 500 ms buffer so the boundary candle is fully closed when the worker wakes.
     */
    public long millisUntilNextBoundaryWithBuffer(ZonedDateTime now) {
        ZonedDateTime current = truncate(now);
        ZonedDateTime nextBound = current.plus(duration);
        long raw = nextBound.toInstant().toEpochMilli() - now.toInstant().toEpochMilli();
        return raw + 500L; // 500 ms buffer
    }

    @Override
    public String toString() {
        return label;
    }
}
