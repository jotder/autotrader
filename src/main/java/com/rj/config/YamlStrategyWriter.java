package com.rj.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes strategy configurations back to a YAML file.
 */
public class YamlStrategyWriter {
    private static final Logger log = LoggerFactory.getLogger(YamlStrategyWriter.class);

    /**
     * Saves all strategies to the given YAML file.
     * Overwrites existing content.
     */
    public void save(Path filePath, Map<String, StrategyYamlConfig> strategies) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        Yaml yaml = new Yaml(options);

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> strategiesMap = new LinkedHashMap<>();
        
        for (Map.Entry<String, StrategyYamlConfig> entry : strategies.entrySet()) {
            strategiesMap.put(entry.getKey(), toMap(entry.getValue()));
        }
        
        root.put("strategies", strategiesMap);

        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            yaml.dump(root, writer);
            log.info("Saved {} strategies to {}", strategies.size(), filePath);
        } catch (IOException e) {
            log.error("Failed to write YAML file {}: {}", filePath, e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> toMap(StrategyYamlConfig cfg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", cfg.isEnabled());
        map.put("symbols", cfg.getSymbols());
        map.put("timeframe", cfg.getTimeframe());
        map.put("cooldown_minutes", cfg.getCooldownMinutes());
        map.put("max_trades_per_day", cfg.getMaxTradesPerDay());

        Map<String, Object> ah = new LinkedHashMap<>();
        ah.put("start", cfg.getActiveHours().getStart());
        ah.put("end", cfg.getActiveHours().getEnd());
        map.put("active_hours", ah);

        Map<String, Object> ind = new LinkedHashMap<>();
        ind.put("ema_fast", cfg.getIndicators().getEmaFast());
        ind.put("ema_slow", cfg.getIndicators().getEmaSlow());
        ind.put("rsi_period", cfg.getIndicators().getRsiPeriod());
        ind.put("atr_period", cfg.getIndicators().getAtrPeriod());
        ind.put("rel_vol_period", cfg.getIndicators().getRelVolPeriod());
        ind.put("min_candles", cfg.getIndicators().getMinCandles());
        map.put("indicators", ind);

        Map<String, Object> ent = new LinkedHashMap<>();
        ent.put("min_confidence", cfg.getEntry().getMinConfidence());
        ent.put("rel_vol_threshold", cfg.getEntry().getRelVolThreshold());
        ent.put("trend_strength", cfg.getEntry().getTrendStrength());
        map.put("entry", ent);

        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("risk_per_trade_pct", cfg.getRisk().getRiskPerTradePct());
        risk.put("sl_atr_multiplier", cfg.getRisk().getSlAtrMultiplier());
        risk.put("tp_r_multiple", cfg.getRisk().getTpRMultiple());
        risk.put("trailing_activation_pct", cfg.getRisk().getTrailingActivationPct());
        risk.put("trailing_step_pct", cfg.getRisk().getTrailingStepPct());
        risk.put("max_exposure_pct", cfg.getRisk().getMaxExposurePct());
        risk.put("max_qty", cfg.getRisk().getMaxQty());
        risk.put("max_consecutive_losses", cfg.getRisk().getMaxConsecutiveLosses());
        map.put("risk", risk);

        Map<String, Object> ord = new LinkedHashMap<>();
        ord.put("type", cfg.getOrder().getType());
        ord.put("slippage_tolerance", cfg.getOrder().getSlippageTolerance());
        ord.put("product_type", cfg.getOrder().getProductType());
        map.put("order", ord);

        return map;
    }
}
