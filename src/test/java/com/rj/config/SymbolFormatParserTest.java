package com.rj.config;

import com.rj.model.ParsedSymbol;
import com.rj.model.SymbolType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Month;

import static org.junit.jupiter.api.Assertions.*;

class SymbolFormatParserTest {

    // ── Equity ──────────────────────────────────────────────────────────────

    @Test
    void parseEquity() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:SBIN-EQ");
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY, p.type());
        assertEquals("NSE", p.exchange());
        assertEquals("SBIN", p.underlying());
        assertEquals("EQ", p.series());
        assertNull(p.year());
        assertNull(p.month());
        assertNull(p.strikePrice());
        assertNull(p.optionType());
        assertFalse(p.isDerivative());
    }

    @Test
    void parseEquityBse() {
        ParsedSymbol p = SymbolFormatParser.parse("BSE:ACC-A");
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY, p.type());
        assertEquals("BSE", p.exchange());
        assertEquals("ACC", p.underlying());
        assertEquals("A", p.series());
    }

    @Test
    void parseEquityBeSeries() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:MODIRUBBER-BE");
        assertNotNull(p);
        assertEquals("MODIRUBBER", p.underlying());
        assertEquals("BE", p.series());
    }

    // ── Equity Futures ──────────────────────────────────────────────────────

    @Test
    void parseEquityFuture() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:NIFTY20OCTFUT");
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_FUTURE, p.type());
        assertEquals("NSE", p.exchange());
        assertEquals("NIFTY", p.underlying());
        assertEquals(2020, p.year());
        assertEquals(Month.OCTOBER, p.month());
        assertTrue(p.isDerivative());
        assertTrue(p.isFuture());
        assertFalse(p.isOption());
    }

    @Test
    void parseEquityFutureBse() {
        ParsedSymbol p = SymbolFormatParser.parse("BSE:SENSEX23AUGFUT");
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_FUTURE, p.type());
        assertEquals("SENSEX", p.underlying());
        assertEquals(2023, p.year());
        assertEquals(Month.AUGUST, p.month());
    }

    @Test
    void parseBankNiftyFuture() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:BANKNIFTY20NOVFUT");
        assertNotNull(p);
        assertEquals("BANKNIFTY", p.underlying());
        assertEquals(Month.NOVEMBER, p.month());
    }

    // ── Equity Options (Monthly) ────────────────────────────────────────────

    @Test
    void parseEquityOptionMonthly() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:NIFTY20OCT11000CE");
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_OPTION_MONTHLY, p.type());
        assertEquals("NIFTY", p.underlying());
        assertEquals(2020, p.year());
        assertEquals(Month.OCTOBER, p.month());
        assertEquals(11000.0, p.strikePrice());
        assertEquals("CE", p.optionType());
        assertTrue(p.isOption());
        assertFalse(p.isWeekly());
    }

    @Test
    void parseEquityOptionMonthlyPut() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:BANKNIFTY20NOV25000PE");
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_OPTION_MONTHLY, p.type());
        assertEquals("BANKNIFTY", p.underlying());
        assertEquals(25000.0, p.strikePrice());
        assertEquals("PE", p.optionType());
    }

    @Test
    void parseEquityOptionMonthlyBse() {
        ParsedSymbol p = SymbolFormatParser.parse("BSE:SENSEX23AUG60400CE");
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_OPTION_MONTHLY, p.type());
        assertEquals("SENSEX", p.underlying());
        assertEquals(60400.0, p.strikePrice());
    }

    // ── Equity Options (Weekly) ─────────────────────────────────────────────

    @Test
    void parseEquityOptionWeeklyOctNumericMonth() {
        // Oct uses 'O' but "NIFTY2010811000CE" has month code '1' (Jan!) and dd=08
        // Actually this example is ambiguous — 20|1|08|11000|CE means YY=20, M=1(Jan), dd=08
        // But the YAML says this is October. Looking closer: "NSE:NIFTY2010811000CE"
        // Parsing: 20 | 1 | 08 | 11000 | CE — M=1 means January, dd=08
        // Wait — the YAML has it as Oct 8th. Let me re-read: {YY}{M}{dd}{Strike}{Opt_Type}
        // "2010811000CE" → YY=20, then we need M+dd. But M is single char.
        // "2010811000CE" → M could be '1' (Jan), dd='08'? No, that leaves '11000CE'
        // Actually: M='1', dd='08', strike='11000', optType='CE' — but the YAML says Oct.
        // The example "NSE:NIFTY20O0811000CE" is the Oct one (O=Oct).
        // "NSE:NIFTY2010811000CE" → M='1'=Jan? But YAML labels it as Oct example.
        // This seems like a YAML documentation ambiguity. Let's test what our parser produces.
        ParsedSymbol p = SymbolFormatParser.parse("NSE:NIFTY2010811000CE");
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_OPTION_WEEKLY, p.type());
        assertEquals("NIFTY", p.underlying());
        assertEquals(2020, p.year());
        // M='1' maps to January in the month code
        assertEquals(Month.JANUARY, p.month());
        assertEquals('1', p.monthCode());
        assertEquals(8, p.dayOfMonth());
        assertEquals(11000.0, p.strikePrice());
        assertEquals("CE", p.optionType());
        assertTrue(p.isWeekly());
    }

    @Test
    void parseEquityOptionWeeklyOctExplicit() {
        // "NSE:NIFTY20O0811000CE" — O=October, dd=08
        ParsedSymbol p = SymbolFormatParser.parse("NSE:NIFTY20O0811000CE");
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_OPTION_WEEKLY, p.type());
        assertEquals(Month.OCTOBER, p.month());
        assertEquals('O', p.monthCode());
        assertEquals(8, p.dayOfMonth());
        assertEquals(11000.0, p.strikePrice());
    }

    @Test
    void parseEquityOptionWeeklyDec() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:NIFTY20D1025000CE");
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_OPTION_WEEKLY, p.type());
        assertEquals(Month.DECEMBER, p.month());
        assertEquals('D', p.monthCode());
        assertEquals(10, p.dayOfMonth());
        assertEquals(25000.0, p.strikePrice());
    }

    @Test
    void parseEquityOptionWeeklyBse() {
        ParsedSymbol p = SymbolFormatParser.parse("BSE:SENSEX2381161000CE");
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_OPTION_WEEKLY, p.type());
        assertEquals("SENSEX", p.underlying());
        assertEquals(2023, p.year());
        assertEquals(Month.AUGUST, p.month());
        assertEquals('8', p.monthCode());
        assertEquals(11, p.dayOfMonth());
        assertEquals(61000.0, p.strikePrice());
    }

    // ── Currency Futures ────────────────────────────────────────────────────

    @Test
    void parseCurrencyFuture() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:USDINR20OCTFUT");
        assertNotNull(p);
        assertEquals(SymbolType.CURRENCY_FUTURE, p.type());
        assertEquals("USDINR", p.underlying());
        assertEquals(Month.OCTOBER, p.month());
    }

    @Test
    void parseCurrencyFutureGbp() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:GBPINR20NOVFUT");
        assertNotNull(p);
        assertEquals(SymbolType.CURRENCY_FUTURE, p.type());
        assertEquals("GBPINR", p.underlying());
    }

    // ── Currency Options (Monthly) ──────────────────────────────────────────

    @Test
    void parseCurrencyOptionMonthly() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:USDINR20OCT75CE");
        assertNotNull(p);
        assertEquals(SymbolType.CURRENCY_OPTION_MONTHLY, p.type());
        assertEquals("USDINR", p.underlying());
        assertEquals(75.0, p.strikePrice());
        assertEquals("CE", p.optionType());
    }

    @Test
    void parseCurrencyOptionMonthlyDecimalStrike() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:GBPINR20NOV80.5PE");
        assertNotNull(p);
        assertEquals(SymbolType.CURRENCY_OPTION_MONTHLY, p.type());
        assertEquals("GBPINR", p.underlying());
        assertEquals(80.5, p.strikePrice());
        assertEquals("PE", p.optionType());
    }

    // ── Currency Options (Weekly) ───────────────────────────────────────────

    @Test
    void parseCurrencyOptionWeekly() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:USDINR20O0875CE");
        assertNotNull(p);
        assertEquals(SymbolType.CURRENCY_OPTION_WEEKLY, p.type());
        assertEquals("USDINR", p.underlying());
        assertEquals(Month.OCTOBER, p.month());
        assertEquals(8, p.dayOfMonth());
        assertEquals(75.0, p.strikePrice());
    }

    @Test
    void parseCurrencyOptionWeeklyDecimalStrike() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:GBPINR20N0580.5PE");
        assertNotNull(p);
        assertEquals(SymbolType.CURRENCY_OPTION_WEEKLY, p.type());
        assertEquals(Month.NOVEMBER, p.month());
        assertEquals(5, p.dayOfMonth());
        assertEquals(80.5, p.strikePrice());
    }

    @Test
    void parseCurrencyOptionWeeklyDec() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:USDINR20D1075CE");
        assertNotNull(p);
        assertEquals(SymbolType.CURRENCY_OPTION_WEEKLY, p.type());
        assertEquals(Month.DECEMBER, p.month());
        assertEquals(10, p.dayOfMonth());
    }

    // ── Commodity Futures ───────────────────────────────────────────────────

    @Test
    void parseCommodityFuture() {
        ParsedSymbol p = SymbolFormatParser.parse("MCX:CRUDEOIL20OCTFUT");
        assertNotNull(p);
        assertEquals(SymbolType.COMMODITY_FUTURE, p.type());
        assertEquals("MCX", p.exchange());
        assertEquals("CRUDEOIL", p.underlying());
        assertEquals(2020, p.year());
        assertEquals(Month.OCTOBER, p.month());
    }

    @Test
    void parseCommodityFutureGold() {
        ParsedSymbol p = SymbolFormatParser.parse("MCX:GOLD20DECFUT");
        assertNotNull(p);
        assertEquals(SymbolType.COMMODITY_FUTURE, p.type());
        assertEquals("GOLD", p.underlying());
    }

    // ── Commodity Options (Monthly) ─────────────────────────────────────────

    @Test
    void parseCommodityOptionMonthly() {
        ParsedSymbol p = SymbolFormatParser.parse("MCX:CRUDEOIL20OCT4000CE");
        assertNotNull(p);
        assertEquals(SymbolType.COMMODITY_OPTION_MONTHLY, p.type());
        assertEquals("CRUDEOIL", p.underlying());
        assertEquals(4000.0, p.strikePrice());
        assertEquals("CE", p.optionType());
    }

    @Test
    void parseCommodityOptionMonthlyPut() {
        ParsedSymbol p = SymbolFormatParser.parse("MCX:GOLD20DEC40000PE");
        assertNotNull(p);
        assertEquals(SymbolType.COMMODITY_OPTION_MONTHLY, p.type());
        assertEquals("GOLD", p.underlying());
        assertEquals(40000.0, p.strikePrice());
        assertEquals("PE", p.optionType());
    }

    // ── Edge cases ──────────────────────────────────────────────────────────

    @Test
    void parseNullReturnsNull() {
        assertNull(SymbolFormatParser.parse(null));
    }

    @Test
    void parseEmptyReturnsNull() {
        assertNull(SymbolFormatParser.parse(""));
        assertNull(SymbolFormatParser.parse("   "));
    }

    @Test
    void parseUnrecognizedReturnsNull() {
        assertNull(SymbolFormatParser.parse("INVALID"));
        assertNull(SymbolFormatParser.parse("FOO:BAR"));
    }

    @Test
    void classifyDelegates() {
        assertEquals(SymbolType.EQUITY, SymbolFormatParser.classify("NSE:SBIN-EQ"));
        assertEquals(SymbolType.EQUITY_FUTURE, SymbolFormatParser.classify("NSE:NIFTY26MARFUT"));
        assertNull(SymbolFormatParser.classify("INVALID"));
    }

    // ── Build + round-trip ──────────────────────────────────────────────────

    @Test
    void buildEquityRoundTrip() {
        String sym = SymbolFormatParser.buildEquity("NSE", "SBIN", "EQ");
        assertEquals("NSE:SBIN-EQ", sym);
        ParsedSymbol p = SymbolFormatParser.parse(sym);
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY, p.type());
    }

    @Test
    void buildFutureRoundTrip() {
        String sym = SymbolFormatParser.buildFuture("NSE", "NIFTY", 2026, Month.MARCH);
        assertEquals("NSE:NIFTY26MARFUT", sym);
        ParsedSymbol p = SymbolFormatParser.parse(sym);
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_FUTURE, p.type());
        assertEquals(2026, p.year());
        assertEquals(Month.MARCH, p.month());
    }

    @Test
    void buildOptionMonthlyRoundTrip() {
        String sym = SymbolFormatParser.buildOptionMonthly("NSE", "NIFTY", 2020, Month.OCTOBER, 11000, "CE");
        assertEquals("NSE:NIFTY20OCT11000CE", sym);
        ParsedSymbol p = SymbolFormatParser.parse(sym);
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_OPTION_MONTHLY, p.type());
        assertEquals(11000.0, p.strikePrice());
    }

    @Test
    void buildOptionWeeklyRoundTrip() {
        String sym = SymbolFormatParser.buildOptionWeekly("NSE", "NIFTY", 2020, Month.OCTOBER, 8, 11000, "CE");
        assertEquals("NSE:NIFTY20O0811000CE", sym);
        ParsedSymbol p = SymbolFormatParser.parse(sym);
        assertNotNull(p);
        assertEquals(SymbolType.EQUITY_OPTION_WEEKLY, p.type());
        assertEquals(Month.OCTOBER, p.month());
        assertEquals(8, p.dayOfMonth());
    }

    @Test
    void buildOptionDecimalStrikeRoundTrip() {
        String sym = SymbolFormatParser.buildOptionMonthly("NSE", "GBPINR", 2020, Month.NOVEMBER, 80.5, "PE");
        assertEquals("NSE:GBPINR20NOV80.5PE", sym);
        ParsedSymbol p = SymbolFormatParser.parse(sym);
        assertNotNull(p);
        assertEquals(80.5, p.strikePrice());
    }

    // ── ExpiryLabel ─────────────────────────────────────────────────────────

    @Test
    void expiryLabelEquityIsNull() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:SBIN-EQ");
        assertNotNull(p);
        assertNull(p.expiryLabel());
    }

    @Test
    void expiryLabelFuture() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:NIFTY26MARFUT");
        assertNotNull(p);
        assertEquals("26MAR", p.expiryLabel());
    }

    @Test
    void expiryLabelWeeklyOption() {
        ParsedSymbol p = SymbolFormatParser.parse("NSE:NIFTY20O0811000CE");
        assertNotNull(p);
        assertEquals("20O08", p.expiryLabel());
    }

    // ── SymbolType helpers ──────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "EQUITY, false, false, false, false",
            "EQUITY_FUTURE, true, false, true, false",
            "EQUITY_OPTION_MONTHLY, true, true, false, false",
            "EQUITY_OPTION_WEEKLY, true, true, false, true",
            "CURRENCY_FUTURE, true, false, true, false",
            "CURRENCY_OPTION_WEEKLY, true, true, false, true",
            "COMMODITY_FUTURE, true, false, true, false",
            "COMMODITY_OPTION_MONTHLY, true, true, false, false"
    })
    void symbolTypeHelpers(SymbolType type, boolean derivative, boolean option,
                           boolean future, boolean weekly) {
        assertEquals(derivative, type.isDerivative());
        assertEquals(option, type.isOption());
        assertEquals(future, type.isFuture());
        assertEquals(weekly, type.isWeekly());
    }
}
