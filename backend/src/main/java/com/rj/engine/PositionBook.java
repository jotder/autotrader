package com.rj.engine;

import com.rj.model.OpenPosition;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class PositionBook {

    private final ConcurrentHashMap<String, OpenPosition> positions = new ConcurrentHashMap<>();

    public void add(OpenPosition position) {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(position.getCorrelationId(), "correlationId must not be null");
        OpenPosition existing = positions.putIfAbsent(position.getCorrelationId(), position);
        if (existing != null) {
            throw new IllegalStateException(
                "Duplicate correlationId in PositionBook: " + position.getCorrelationId());
        }
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
