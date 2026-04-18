package com.rj.model;

import com.rj.model.dim.Exchange;
import com.rj.model.dim.InstrumentType;
import com.rj.model.dim.Segment;
import java.time.LocalDate;

/**
 * High-performance Instrument entity for the EclipseStore Object Graph.
 * Holds direct references to dimension entities for fast traversal.
 */
public class SymbolMaster {
    // Primary Identifiers
    private String fyToken;
    private String isin;
    private String exchangeSymbol;
    private String symbolDetails;
    private String symbolTicker;
    
    // Dimension References
    private Exchange exchange;
    private Segment segment;
    private InstrumentType instrumentType;
    
    // Metadata
    private String exchangeSymbolName;
    private int exchangeToken;
    private String series;
    private String optionType; // CE, PE, XX
    private String underlyingSymbol;
    private String underlyingFyToken;
    
    // Trading Parameters
    private int minLotSize;
    private double tickSize;
    private String tradingSession;
    private LocalDate lastUpdate;
    private LocalDate expiryDate;
    private double strikePrice;
    private String qtyFreeze;
    private boolean tradeable;
    
    // Financials
    private String currencyCode;
    private double faceValue;
    private double qtyMultiplier;
    
    // Margin Trading Facility (MTF)
    private boolean mtfTradable;
    private double mtfMargin;

    // Stream
    private String streamGroup;

    // Getters and Setters
    public String getFyToken() { return fyToken; }
    public void setFyToken(String fyToken) { this.fyToken = fyToken; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getExchangeSymbol() { return exchangeSymbol; }
    public void setExchangeSymbol(String exchangeSymbol) { this.exchangeSymbol = exchangeSymbol; }

    public String getSymbolDetails() { return symbolDetails; }
    public void setSymbolDetails(String symbolDetails) { this.symbolDetails = symbolDetails; }

    public String getSymbolTicker() { return symbolTicker; }
    public void setSymbolTicker(String symbolTicker) { this.symbolTicker = symbolTicker; }

    public Exchange getExchange() { return exchange; }
    public void setExchange(Exchange exchange) { this.exchange = exchange; }

    public Segment getSegment() { return segment; }
    public void setSegment(Segment segment) { this.segment = segment; }

    public InstrumentType getInstrumentType() { return instrumentType; }
    public void setInstrumentType(InstrumentType instrumentType) { this.instrumentType = instrumentType; }

    public String getExchangeSymbolName() { return exchangeSymbolName; }
    public void setExchangeSymbolName(String exchangeSymbolName) { this.exchangeSymbolName = exchangeSymbolName; }

    public int getExchangeToken() { return exchangeToken; }
    public void setExchangeToken(int exchangeToken) { this.exchangeToken = exchangeToken; }

    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }

    public String getOptionType() { return optionType; }
    public void setOptionType(String optionType) { this.optionType = optionType; }

    public String getUnderlyingSymbol() { return underlyingSymbol; }
    public void setUnderlyingSymbol(String underlyingSymbol) { this.underlyingSymbol = underlyingSymbol; }

    public String getUnderlyingFyToken() { return underlyingFyToken; }
    public void setUnderlyingFyToken(String underlyingFyToken) { this.underlyingFyToken = underlyingFyToken; }

    public int getMinLotSize() { return minLotSize; }
    public void setMinLotSize(int minLotSize) { this.minLotSize = minLotSize; }

    public double getTickSize() { return tickSize; }
    public void setTickSize(double tickSize) { this.tickSize = tickSize; }

    public String getTradingSession() { return tradingSession; }
    public void setTradingSession(String tradingSession) { this.tradingSession = tradingSession; }

    public LocalDate getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(LocalDate lastUpdate) { this.lastUpdate = lastUpdate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public double getStrikePrice() { return strikePrice; }
    public void setStrikePrice(double strikePrice) { this.strikePrice = strikePrice; }

    public String getQtyFreeze() { return qtyFreeze; }
    public void setQtyFreeze(String qtyFreeze) { this.qtyFreeze = qtyFreeze; }

    public boolean isTradeable() { return tradeable; }
    public void setTradeable(boolean tradeable) { this.tradeable = tradeable; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public double getFaceValue() { return faceValue; }
    public void setFaceValue(double faceValue) { this.faceValue = faceValue; }

    public double getQtyMultiplier() { return qtyMultiplier; }
    public void setQtyMultiplier(double qtyMultiplier) { this.qtyMultiplier = qtyMultiplier; }

    public boolean isMtfTradable() { return mtfTradable; }
    public void setMtfTradable(boolean mtfTradable) { this.mtfTradable = mtfTradable; }

    public double getMtfMargin() { return mtfMargin; }
    public void setMtfMargin(double mtfMargin) { this.mtfMargin = mtfMargin; }

    public String getStreamGroup() { return streamGroup; }
    public void setStreamGroup(String streamGroup) { this.streamGroup = streamGroup; }

    @Override
    public String toString() {
        return "SymbolMaster{" +
                "ticker='" + symbolTicker + '\'' +
                ", exchange=" + (exchange != null ? exchange.name() : "null") +
                ", segment=" + (segment != null ? segment.name() : "null") +
                '}';
    }
}
