package com.rj.engine;

import com.rj.model.OpenPosition;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class PositionBook {

    private final ConcurrentHashMap<String, OpenPosition> positions = new ConcurrentHashMap<>();

    public void add(OpenPosition position) {
        positions.put(position.getCorrelationId(), position);
    }

    public OpenPosition remove(String correlationId) {
        return positions.remove(correlationId);
    }

    public OpenPosition get(String correlationId) {
        return positions.get(correlationId);
    }

    public Collection<OpenPosition> openPositions() {
        return Collections.unmodifiableCollection(positions.values());
    }

    public Collection<OpenPosition> values() {
        return positions.values();
    }

    public int openPositionCount() {
        return positions.size();
    }

    public boolean hasOpenPosition(String symbol) {
        return positions.values().stream().anyMatch(p -> p.getSymbol().equals(symbol));
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }
}
