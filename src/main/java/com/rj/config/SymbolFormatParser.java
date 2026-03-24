package com.rj.config;

import com.rj.model.ParsedSymbol;
import com.rj.model.SymbolType;

import java.time.Month;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and constructs Fyers symbol strings.
 * <p>
 * Stateless and thread-safe — all methods are static, all patterns are
 * pre-compiled. Derived from the format templates in
 * {@code config/symbol_format.yaml}.
 *
 * <h3>Format examples</h3>
 * <ul>
 *   <li>Equity: {@code NSE:SBIN-EQ}, {@code BSE:ACC-A}</li>
 *   <li>Futures: {@code NSE:NIFTY26MARFUT}, {@code MCX:CRUDEOIL20OCTFUT}</li>
 *   <li>Options (monthly): {@code NSE:NIFTY20OCT11000CE}</li>
 *   <li>Options (weekly): {@code NSE:NIFTY2010811000CE} (Oct 8th)</li>
 * </ul>
 */
public final class SymbolFormatParser {

    private SymbolFormatParser() {}

    // ── Regex patterns (most specific first) ────────────────────────────────

    private static final String EX = "(NSE|BSE|MCX)";
    private static final String YY = "(\\d{2})";
    private static final String MMM = "(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)";
    private static final String M_WEEKLY = "([1-9ONOD])";
    private static final String DD = "(\\d{2})";
    private static final String STRIKE = "(\\d+(?:\\.\\d+)?)";
    private static final String OPT = "(CE|PE)";

    // Weekly options: {Ex}:{Underlying}{YY}{M}{dd}{Strike}{Opt_Type}
    // Must be tried before monthly options (more specific)
    private static final Pattern WEEKLY_OPT = Pattern.compile(
            "^" + EX + ":(.+?)" + YY + M_WEEKLY + DD + STRIKE + OPT + "$");

    // Monthly options: {Ex}:{Underlying}{YY}{MMM}{Strike}{Opt_Type}
    private static final Pattern MONTHLY_OPT = Pattern.compile(
            "^" + EX + ":(.+?)" + YY + MMM + STRIKE + OPT + "$");

    // Futures: {Ex}:{Underlying}{YY}{MMM}FUT
    private static final Pattern FUTURE = Pattern.compile(
            "^" + EX + ":(.+?)" + YY + MMM + "FUT$");

    // Equity: {Ex}:{Symbol}-{Series}
    private static final Pattern EQUITY = Pattern.compile(
            "^" + EX + ":(.+)-([A-Z]+)$");

    // ── Month code mapping for weekly expiry ────────────────────────────────

    private static final Map<Character, Month> WEEKLY_MONTH_MAP = Map.ofEntries(
            Map.entry('1', Month.JANUARY),
            Map.entry('2', Month.FEBRUARY),
            Map.entry('3', Month.MARCH),
            Map.entry('4', Month.APRIL),
            Map.entry('5', Month.MAY),
            Map.entry('6', Month.JUNE),
            Map.entry('7', Month.JULY),
            Map.entry('8', Month.AUGUST),
            Map.entry('9', Month.SEPTEMBER),
            Map.entry('O', Month.OCTOBER),
            Map.entry('N', Month.NOVEMBER),
            Map.entry('D', Month.DECEMBER)
    );

    private static final Map<Month, Character> MONTH_TO_WEEKLY_CODE = Map.ofEntries(
            Map.entry(Month.JANUARY, '1'),
            Map.entry(Month.FEBRUARY, '2'),
            Map.entry(Month.MARCH, '3'),
            Map.entry(Month.APRIL, '4'),
            Map.entry(Month.MAY, '5'),
            Map.entry(Month.JUNE, '6'),
            Map.entry(Month.JULY, '7'),
            Map.entry(Month.AUGUST, '8'),
            Map.entry(Month.SEPTEMBER, '9'),
            Map.entry(Month.OCTOBER, 'O'),
            Map.entry(Month.NOVEMBER, 'N'),
            Map.entry(Month.DECEMBER, 'D')
    );

    private static final Map<String, Month> MMM_TO_MONTH = Map.ofEntries(
            Map.entry("JAN", Month.JANUARY), Map.entry("FEB", Month.FEBRUARY),
            Map.entry("MAR", Month.MARCH), Map.entry("APR", Month.APRIL),
            Map.entry("MAY", Month.MAY), Map.entry("JUN", Month.JUNE),
            Map.entry("JUL", Month.JULY), Map.entry("AUG", Month.AUGUST),
            Map.entry("SEP", Month.SEPTEMBER), Map.entry("OCT", Month.OCTOBER),
            Map.entry("NOV", Month.NOVEMBER), Map.entry("DEC", Month.DECEMBER)
    );

    // ── Known currency pairs and commodity names ────────────────────────────
    // Used to disambiguate between equity and currency/commodity derivatives

    private static final String[] CURRENCY_PAIRS = {
            "USDINR", "GBPINR", "EURINR", "JPYINR"
    };

    private static final String[] COMMODITY_PREFIXES = {
            "CRUDEOIL", "GOLD", "GOLDM", "SILVER", "SILVERM", "COPPER",
            "NATURALGAS", "ALUMINIUM", "ALUMINI", "ZINC", "LEAD", "NICKEL",
            "COTTON", "MENTHAOIL", "MCXBULLDEX", "MCXMETLDEX"
    };

    // ── Parse ───────────────────────────────────────────────────────────────

    /**
     * Parse a Fyers symbol string into structured parts.
     *
     * @param symbol e.g. {@code "NSE:SBIN-EQ"}, {@code "NSE:NIFTY26MARFUT"}
     * @return parsed symbol, or {@code null} if the format is unrecognized
     */
    public static ParsedSymbol parse(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;

        Matcher m;

        // 1. Try weekly options (most specific)
        m = WEEKLY_OPT.matcher(symbol);
        if (m.matches()) {
            String exchange = m.group(1);
            String underlying = m.group(2);
            int year = 2000 + Integer.parseInt(m.group(3));
            char monthCode = m.group(4).charAt(0);
            int day = Integer.parseInt(m.group(5));
            double strike = Double.parseDouble(m.group(6));
            String optType = m.group(7);
            SymbolType type = classifyWeeklyOption(exchange, underlying);
            Month month = WEEKLY_MONTH_MAP.get(monthCode);
            return new ParsedSymbol(symbol, type, exchange, underlying, null,
                    year, month, monthCode, day, strike, optType);
        }

        // 2. Try monthly options
        m = MONTHLY_OPT.matcher(symbol);
        if (m.matches()) {
            String exchange = m.group(1);
            String underlying = m.group(2);
            int year = 2000 + Integer.parseInt(m.group(3));
            Month month = MMM_TO_MONTH.get(m.group(4));
            double strike = Double.parseDouble(m.group(5));
            String optType = m.group(6);
            SymbolType type = classifyMonthlyOption(exchange, underlying);
            return new ParsedSymbol(symbol, type, exchange, underlying, null,
                    year, month, null, null, strike, optType);
        }

        // 3. Try futures
        m = FUTURE.matcher(symbol);
        if (m.matches()) {
            String exchange = m.group(1);
            String underlying = m.group(2);
            int year = 2000 + Integer.parseInt(m.group(3));
            Month month = MMM_TO_MONTH.get(m.group(4));
            SymbolType type = classifyFuture(exchange, underlying);
            return new ParsedSymbol(symbol, type, exchange, underlying, null,
                    year, month, null, null, null, null);
        }

        // 4. Try equity
        m = EQUITY.matcher(symbol);
        if (m.matches()) {
            String exchange = m.group(1);
            String ticker = m.group(2);
            String series = m.group(3);
            return new ParsedSymbol(symbol, SymbolType.EQUITY, exchange, ticker, series,
                    null, null, null, null, null, null);
        }

        return null;
    }

    /**
     * Classify a symbol without full parsing.
     *
     * @return the symbol type, or {@code null} if unrecognized
     */
    public static SymbolType classify(String symbol) {
        ParsedSymbol parsed = parse(symbol);
        return parsed != null ? parsed.type() : null;
    }

    // ── Build ───────────────────────────────────────────────────────────────

    /** Build an equity symbol: {@code NSE:SBIN-EQ} */
    public static String buildEquity(String exchange, String ticker, String series) {
        return exchange + ":" + ticker + "-" + series;
    }

    /** Build a futures symbol: {@code NSE:NIFTY26MARFUT} */
    public static String buildFuture(String exchange, String underlying, int year, Month month) {
        return exchange + ":" + underlying + String.format("%02d", year % 100)
                + month.name().substring(0, 3) + "FUT";
    }

    /** Build a monthly option symbol: {@code NSE:NIFTY26MAR11000CE} */
    public static String buildOptionMonthly(String exchange, String underlying,
                                            int year, Month month,
                                            double strike, String optType) {
        return exchange + ":" + underlying + String.format("%02d", year % 100)
                + month.name().substring(0, 3) + formatStrike(strike) + optType;
    }

    /** Build a weekly option symbol: {@code NSE:NIFTY2610811000CE} */
    public static String buildOptionWeekly(String exchange, String underlying,
                                           int year, Month month, int day,
                                           double strike, String optType) {
        char monthCode = MONTH_TO_WEEKLY_CODE.getOrDefault(month, '?');
        return exchange + ":" + underlying + String.format("%02d", year % 100)
                + monthCode + String.format("%02d", day)
                + formatStrike(strike) + optType;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static SymbolType classifyFuture(String exchange, String underlying) {
        if ("MCX".equals(exchange) || isCommodity(underlying)) return SymbolType.COMMODITY_FUTURE;
        if (isCurrency(underlying)) return SymbolType.CURRENCY_FUTURE;
        return SymbolType.EQUITY_FUTURE;
    }

    private static SymbolType classifyMonthlyOption(String exchange, String underlying) {
        if ("MCX".equals(exchange) || isCommodity(underlying)) return SymbolType.COMMODITY_OPTION_MONTHLY;
        if (isCurrency(underlying)) return SymbolType.CURRENCY_OPTION_MONTHLY;
        return SymbolType.EQUITY_OPTION_MONTHLY;
    }

    private static SymbolType classifyWeeklyOption(String exchange, String underlying) {
        if (isCurrency(underlying)) return SymbolType.CURRENCY_OPTION_WEEKLY;
        return SymbolType.EQUITY_OPTION_WEEKLY;
    }

    private static boolean isCurrency(String underlying) {
        for (String pair : CURRENCY_PAIRS) {
            if (underlying.equals(pair)) return true;
        }
        return false;
    }

    private static boolean isCommodity(String underlying) {
        for (String prefix : COMMODITY_PREFIXES) {
            if (underlying.equals(prefix)) return true;
        }
        return false;
    }

    private static String formatStrike(double strike) {
        if (strike == Math.floor(strike) && !Double.isInfinite(strike)) {
            return String.valueOf((long) strike);
        }
        // Remove trailing zeros: 80.50 -> 80.5
        String s = String.valueOf(strike);
        if (s.endsWith("0") && s.contains(".")) {
            s = s.replaceAll("0+$", "");
        }
        return s;
    }
}
