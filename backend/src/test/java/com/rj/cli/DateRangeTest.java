package com.rj.cli;

import com.rj.config.StrategyYamlConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    @Test
    void businessDays_skipsWeekends() {
        DateRange r = new DateRange(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 7)); // Fri..Tue
        assertThat(r.businessDays()).containsExactly(
                LocalDate.of(2026, 4, 3),
                LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 4, 7));
    }

    @Test
    void constructor_rejectsFromAfterTo() {
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void businessDaysIntersect_onlyKeepsAvailable() {
        DateRange r = new DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 5));
        List<LocalDate> avail = List.of(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 6));
        assertThat(r.businessDaysIntersect(avail))
                .containsExactly(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3));
    }

    @Test
    void resolve_yamlBlockWithBothDates_used() {
        StrategyYamlConfig.BacktestBlock bt = new StrategyYamlConfig.BacktestBlock();
        bt.setFrom(LocalDate.of(2026, 4, 1));
        bt.setTo(LocalDate.of(2026, 4, 10));
        DateRange r = DateRange.resolve(bt);
        assertThat(r.isEmpty()).isFalse();
        assertThat(r.from()).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void resolve_partialYamlBlock_throws() {
        StrategyYamlConfig.BacktestBlock bt = new StrategyYamlConfig.BacktestBlock();
        bt.setFrom(LocalDate.of(2026, 4, 1));
        assertThatThrownBy(() -> DateRange.resolve(bt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both");
    }

    @Test
    void resolve_nullYamlBlock_returnsEmpty() {
        DateRange r = DateRange.resolve(null);
        assertThat(r.isEmpty()).isTrue();
    }

    @Test
    void businessDays_emptyRange_returnsEmptyList() {
        assertThat(DateRange.empty().businessDays()).isEmpty();
    }
}
