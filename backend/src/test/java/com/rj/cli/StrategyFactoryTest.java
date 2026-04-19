package com.rj.cli;

import com.rj.config.StrategyYamlConfig;
import com.rj.strategy.ITradeStrategy;
import com.rj.strategy.MultiTimeframeVotingStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyFactoryTest {

    @Test
    void from_yamlConfig_returnsConfiguredStrategy() {
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        cfg.getEntry().setMinConfidence(0.85);
        cfg.getRisk().setSlAtrMultiplier(2.0);
        cfg.getRisk().setTpRMultiple(3.0);

        ITradeStrategy s = StrategyFactory.from(cfg, "trend_following");
        assertThat(s).isInstanceOf(MultiTimeframeVotingStrategy.class);
        assertThat(s.getId()).isEqualTo("trend_following");
    }
}
