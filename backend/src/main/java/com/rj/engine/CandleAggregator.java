package com.rj.engine;

import com.rj.model.Candle;
import com.rj.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Aggregates M1 candles into a higher timeframe. Bars align to IST wall-clock boundaries. */
public final class CandleAggregator {

    private CandleAggregator() {}

    /**
     * Aggregate {@code m1} bars into {@code tf}. Requires the input to be a contiguous, gap-free,
     * ascending M1 sequence. Partial windows (fewer than the expected number of M1 bars) are
     * dropped so consumers only see closed higher-timeframe bars.
     *
     * <p>{@link Timeframe#M1} is a no-op — returns an immutable copy.
     *
     * <p>Note for {@link Timeframe#H1}: {@link Timeframe#truncate} floors to UTC hour boundaries,
     * which in IST fall at HH:30. The NSE session opens at 09:15 IST, so the first 15 bars of each
     * session fall in the 08:30-IST bucket and are dropped as partial; the first full H1 bar of
     * the day is 09:30-10:29 IST.
     */
    public static List<Candle> to(Timeframe tf, List<Candle> m1) {
        if (tf == Timeframe.M1) return List.copyOf(m1);

        int windowSize = (int) (tf.getDuration().toMinutes());
        List<Candle> out = new ArrayList<>();
        List<Candle> buf = new ArrayList<>(windowSize);
        ZonedDateTime currentBucket = null;

        for (Candle c : m1) {
            ZonedDateTime bucket = tf.truncate(c.timestamp);
            if (currentBucket != null && !bucket.equals(currentBucket)) {
                if (buf.size() == windowSize) out.add(combine(buf));
                buf.clear();
            }
            currentBucket = bucket;
            buf.add(c);
        }
        if (buf.size() == windowSize) out.add(combine(buf));
        return out;
    }

    private static Candle combine(List<Candle> bars) {
        long epochSecs = bars.getFirst().timestamp.toEpochSecond();
        double open = bars.getFirst().open;
        double close = bars.getLast().close;
        double high = bars.stream().mapToDouble(b -> b.high).max().orElse(open);
        double low = bars.stream().mapToDouble(b -> b.low).min().orElse(open);
        long vol = bars.stream().mapToLong(b -> b.volume).sum();
        return Candle.of(epochSecs, open, high, low, close, vol);
    }
}
