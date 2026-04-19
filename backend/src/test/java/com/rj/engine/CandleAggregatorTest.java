package com.rj.engine;

import com.rj.model.Candle;
import com.rj.model.Timeframe;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandleAggregatorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static Candle bar(int hh, int mm, double o, double h, double l, double c, long v) {
        long epoch = ZonedDateTime.of(LocalDate.of(2026, 4, 1), java.time.LocalTime.of(hh, mm), IST)
                .toEpochSecond();
        return Candle.of(epoch, o, h, l, c, v);
    }

    @Test
    void aggregate_m1ToM5_groupsEveryFiveBars() {
        List<Candle> m1 = new ArrayList<>();
        for (int m = 15; m < 25; m++) {
            m1.add(bar(9, m, 100 + m, 105 + m, 95 + m, 102 + m, 1000));
        }
        List<Candle> m5 = CandleAggregator.to(Timeframe.M5, m1);

        assertThat(m5).hasSize(2);
        assertThat(m5.get(0).open).isEqualTo(115.0);
        assertThat(m5.get(0).close).isEqualTo(121.0);
        assertThat(m5.get(0).volume).isEqualTo(5000);
        assertThat(m5.get(0).timestamp.getMinute()).isEqualTo(15);
    }

    @Test
    void aggregate_m1ToM15_alignsToWallClock() {
        List<Candle> m1 = new ArrayList<>();
        for (int m = 15; m < 30; m++) {
            m1.add(bar(9, m, 100, 101, 99, 100.5, 10));
        }
        List<Candle> m15 = CandleAggregator.to(Timeframe.M15, m1);

        assertThat(m15).hasSize(1);
        assertThat(m15.get(0).timestamp.getMinute()).isEqualTo(15);
        assertThat(m15.get(0).volume).isEqualTo(150);
    }

    @Test
    void aggregate_dropsPartialTrailingBar() {
        List<Candle> m1 = new ArrayList<>();
        m1.add(bar(9, 15, 100, 101, 99, 100, 10));
        m1.add(bar(9, 16, 100, 101, 99, 100, 10));
        m1.add(bar(9, 17, 100, 101, 99, 100, 10));
        assertThat(CandleAggregator.to(Timeframe.M5, m1)).isEmpty();
    }

    @Test
    void aggregate_m1ToM1_returnsInputUnchanged() {
        List<Candle> m1 = List.of(bar(9, 15, 100, 101, 99, 100, 10));
        assertThat(CandleAggregator.to(Timeframe.M1, m1)).isEqualTo(m1);
    }

    @Test
    void aggregate_highAndLow_areExtremesAcrossBars() {
        List<Candle> m1 = new ArrayList<>();
        m1.add(bar(9, 15, 100, 110, 98, 102, 10));
        m1.add(bar(9, 16, 102, 105,  95, 100, 10));
        m1.add(bar(9, 17, 100, 115,  99, 110, 10));
        m1.add(bar(9, 18, 110, 112, 108, 111, 10));
        m1.add(bar(9, 19, 111, 113, 107, 109, 10));
        List<Candle> m5 = CandleAggregator.to(Timeframe.M5, m1);

        assertThat(m5).hasSize(1);
        assertThat(m5.get(0).high).isEqualTo(115.0);
        assertThat(m5.get(0).low).isEqualTo(95.0);
        assertThat(m5.get(0).open).isEqualTo(100.0);
        assertThat(m5.get(0).close).isEqualTo(109.0);
    }
}
