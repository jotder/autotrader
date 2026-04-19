package com.rj.config;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class StrategyYamlConfigBacktestBlockTest {

    @Test
    void defaultBacktestBlock_isNull() {
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        assertThat(cfg.getBacktest()).isNull();
    }

    @Test
    void setBacktest_roundTrips() {
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        StrategyYamlConfig.BacktestBlock bt = new StrategyYamlConfig.BacktestBlock();
        bt.setFrom(LocalDate.of(2026, 4, 1));
        bt.setTo(LocalDate.of(2026, 4, 10));
        cfg.setBacktest(bt);

        assertThat(cfg.getBacktest()).isNotNull();
        assertThat(cfg.getBacktest().getFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(cfg.getBacktest().getTo()).isEqualTo(LocalDate.of(2026, 4, 10));
    }

    @Test
    void backtestBlock_bothFieldsNullable() {
        StrategyYamlConfig.BacktestBlock bt = new StrategyYamlConfig.BacktestBlock();
        assertThat(bt.getFrom()).isNull();
        assertThat(bt.getTo()).isNull();
    }
}
