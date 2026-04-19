package com.rj.cli;

import com.rj.config.MarketCategory;
import com.rj.config.SymbolRegistry;

import java.util.List;
import java.util.stream.Collectors;

public final class SymbolSelector {
    private SymbolSelector() {}

    public static List<String> select(SymbolRegistry reg, String category, int limit, int offset) {
        MarketCategory cat = MarketCategory.valueOf(category);
        List<String> all = reg.symbolsFor(cat);
        if (all == null || all.isEmpty()) {
            throw new IllegalStateException("no symbols for category: " + cat);
        }
        var stream = all.stream().skip(Math.max(0, offset));
        if (limit > 0) stream = stream.limit(limit);
        List<String> result = stream.collect(Collectors.toUnmodifiableList());
        if (result.isEmpty()) {
            throw new IllegalStateException("no symbols after offset=" + offset + " limit=" + limit);
        }
        return result;
    }
}
