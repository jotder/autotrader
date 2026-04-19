package com.rj.cli;

import com.rj.config.StrategyYamlConfig;
import com.rj.strategy.ITradeStrategy;
import com.rj.strategy.MultiTimeframeVotingStrategy;

public final class StrategyFactory {
    private StrategyFactory() {}

    public static ITradeStrategy from(StrategyYamlConfig cfg, String id) {
        double minConf = cfg.getEntry().getMinConfidence();
        double sl = cfg.getRisk().getSlAtrMultiplier();
        double tp = cfg.getRisk().getTpRMultiple();
        return new MultiTimeframeVotingStrategy(id, id, minConf, sl, tp);
    }
}
