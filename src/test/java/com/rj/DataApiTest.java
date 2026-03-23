import com.rj.model.Candle;
import com.rj.model.MarketDepthResult;
import com.rj.model.OptionChainResult;
import com.rj.model.QuoteEntry;
import com.tts.in.model.StockHistoryModel;
import fyers.FyersDataApi;

void main() {
    FyersDataApi app = new FyersDataApi();

    // 1. Stock Quotes (comma-separated symbols for multiple)
    List<QuoteEntry> quotes = app.getStockQuotes("NSE:TCS-EQ");
    System.out.println("Quotes: " + quotes);

    // 2. Market Depth
    MarketDepthResult depth = app.getMarketDepth("NSE:TCS-EQ", 0);
    System.out.println("Market Depth: " + depth);

    // 3. Historical candles (30-min, epoch date format)
    StockHistoryModel hist = new StockHistoryModel();
    hist.Symbol = "NSE:SBIN-EQ";
    hist.Resolution = "30";
    hist.DateFormat = "1";
    hist.RangeFrom = "2021-01-01";
    hist.RangeTo = "2021-01-02";
    hist.ContFlag = 1;
    List<Candle> history = app.getStockHistory(hist);
    System.out.println("Stock History: " + history);

    // 4. Option Chain
    OptionChainResult chain = app.getOptionChain("NSE:TCS-EQ", 1, "");
    System.out.println("Option Chain: " + chain);
}
