package com.rj.cli;

import com.rj.config.StrategyYamlConfig;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from > to: " + from + " > " + to);
        }
    }

    public boolean isEmpty() { return from == null || to == null; }

    public static DateRange empty() { return new DateRange(null, null); }

    /** Read -Dfrom / -Dto; both required. */
    public static DateRange fromSystemProps() {
        String f = System.getProperty("from");
        String t = System.getProperty("to");
        if (f == null || t == null) {
            throw new IllegalArgumentException("-Dfrom=YYYY-MM-DD and -Dto=YYYY-MM-DD required");
        }
        return new DateRange(LocalDate.parse(f), LocalDate.parse(t));
    }

    /** -Dfrom/-Dto wins; else YAML; else empty. */
    public static DateRange resolve(StrategyYamlConfig.BacktestBlock yaml) {
        String f = System.getProperty("from");
        String t = System.getProperty("to");
        if (f != null && t != null) return new DateRange(LocalDate.parse(f), LocalDate.parse(t));
        if (f != null || t != null) throw new IllegalArgumentException("-Dfrom and -Dto must be set together");

        if (yaml == null) return empty();
        if ((yaml.getFrom() == null) != (yaml.getTo() == null)) {
            throw new IllegalArgumentException("backtest: block must set both from and to (or neither)");
        }
        if (yaml.getFrom() == null) return empty();
        return new DateRange(yaml.getFrom(), yaml.getTo());
    }

    public List<LocalDate> businessDays() {
        if (isEmpty()) return List.of();
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) out.add(d);
        }
        return out;
    }

    public List<LocalDate> businessDaysIntersect(List<LocalDate> available) {
        if (isEmpty()) return List.copyOf(available);
        var set = new HashSet<>(available);
        return businessDays().stream().filter(set::contains).toList();
    }
}
