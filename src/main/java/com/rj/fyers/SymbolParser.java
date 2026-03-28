package com.rj.fyers;

import com.rj.model.SymbolType;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to parse Fyers symbol formats and extract metadata (expiry, strike, option type).
 * 
 * <h3>Formats:</h3>
 * <ul>
 *   <li><b>Equity:</b> NSE:SBIN-EQ, BSE:TCS-EQ</li>
 *   <li><b>Future:</b> NSE:NIFTY26MARFUT, NSE:SBIN26MARFUT</li>
 *   <li><b>Option:</b> NSE:NIFTY26MAR21000CE, NSE:BANKNIFTY2632721500PE (Weekly)</li>
 * </ul>
 */
public final class SymbolParser {

    // NSE:NIFTY26MARFUT or NSE:SBIN26MARFUT
    private static final Pattern FUTURE_PATTERN = Pattern.compile("^(NSE|BSE|MCX):([A-Z&]+)(\\d{2})([A-Z]{3})FUT$");
    
    // NSE:NIFTY26MAR21000CE (Monthly)
    private static final Pattern OPTION_MONTHLY_PATTERN = Pattern.compile("^(NSE|BSE|MCX):([A-Z&]+)(\\d{2})([A-Z]{3})(\\d+)(CE|PE)$");
    
    // NSE:NIFTY2632721000CE (Weekly: 26=Year, 3=Month(Mar), 27=Day)
    private static final Pattern OPTION_WEEKLY_PATTERN = Pattern.compile("^(NSE|BSE):([A-Z&]+)(\\d{2})(\\d)(\\d{2})(\\d+)(CE|PE)$");

    public record SymbolMetadata(
            SymbolType type,
            String baseSymbol,
            String expiry,
            Double strike,
            String optionType
    ) {}

    public static SymbolMetadata parse(String symbol) {
        if (symbol == null) return null;

        // 1. Check Futures
        Matcher fut = FUTURE_PATTERN.matcher(symbol);
        if (fut.matches()) {
            return new SymbolMetadata(SymbolType.EQUITY_FUTURE, fut.group(2), 
                    fut.group(3) + fut.group(4), null, null);
        }

        // 2. Check Monthly Options
        Matcher optM = OPTION_MONTHLY_PATTERN.matcher(symbol);
        if (optM.matches()) {
            return new SymbolMetadata(SymbolType.EQUITY_OPTION_MONTHLY, optM.group(2),
                    optM.group(3) + optM.group(4), Double.parseDouble(optM.group(5)), optM.group(6));
        }

        // 3. Check Weekly Options
        Matcher optW = OPTION_WEEKLY_PATTERN.matcher(symbol);
        if (optW.matches()) {
            return new SymbolMetadata(SymbolType.EQUITY_OPTION_WEEKLY, optW.group(2),
                    optW.group(3) + optW.group(4) + optW.group(5), 
                    Double.parseDouble(optW.group(6)), optW.group(7));
        }

        // 4. Default to Equity
        if (symbol.endsWith("-EQ") || symbol.contains("-INDEX")) {
            return new SymbolMetadata(SymbolType.EQUITY, symbol.split(":")[1].replace("-EQ", ""), 
                    null, null, null);
        }

        return new SymbolMetadata(SymbolType.EQUITY, symbol, null, null, null);
    }

    /**
     * Resolve lot size based on base symbol. 
     * Note: In a production app, this should be fetched from a master API or CSV.
     */
    public static int getLotSize(String baseSymbol) {
        return switch (baseSymbol) {
            case "NIFTY" -> 25;
            case "BANKNIFTY" -> 15;
            case "FINNIFTY" -> 25;
            case "MIDCPNIFTY" -> 50;
            case "CRUDEOIL" -> 100;
            case "GOLD" -> 100;
            case "SILVER" -> 30;
            default -> 1;
        };
    }
}
