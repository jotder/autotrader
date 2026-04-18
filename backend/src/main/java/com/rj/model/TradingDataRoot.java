package com.rj.model;

import com.rj.model.dim.Exchange;
import com.rj.model.dim.InstrumentType;
import com.rj.model.dim.ProductType;
import com.rj.model.dim.Segment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence Root for EclipseStore.
 * This class forms the entry point for the entire in-memory Object Graph.
 */
public class TradingDataRoot {
    // Dimension Lookups
    private final Map<Integer, Exchange> exchanges = new ConcurrentHashMap<>();
    private final Map<Integer, Segment> segments = new ConcurrentHashMap<>();
    private final Map<Integer, InstrumentType> instrumentTypes = new ConcurrentHashMap<>();
    private final Map<String, ProductType> productTypes = new ConcurrentHashMap<>();
    
    // Symbol Master lookups
    private final Map<String, SymbolMaster> symbolsByFyToken = new ConcurrentHashMap<>();
    private final Map<String, SymbolMaster> symbolsByTicker = new ConcurrentHashMap<>();
    
    // Configuration
    private final List<SymbologyFormat> symbologyFormats = new ArrayList<>();

    // Getters
    public Map<Integer, Exchange> getExchanges() { return exchanges; }
    public Map<Integer, Segment> getSegments() { return segments; }
    public Map<Integer, InstrumentType> getInstrumentTypes() { return instrumentTypes; }
    public Map<String, ProductType> getProductTypes() { return productTypes; }
    public Map<String, SymbolMaster> getSymbolsByFyToken() { return symbolsByFyToken; }
    public Map<String, SymbolMaster> getSymbolsByTicker() { return symbolsByTicker; }
    public List<SymbologyFormat> getSymbologyFormats() { return symbologyFormats; }

    /**
     * Clear all data from the root. Use with caution.
     */
    public void clear() {
        exchanges.clear();
        segments.clear();
        instrumentTypes.clear();
        productTypes.clear();
        symbolsByFyToken.clear();
        symbolsByTicker.clear();
        symbologyFormats.clear();
    }
}
