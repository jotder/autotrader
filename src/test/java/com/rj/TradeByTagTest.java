import com.rj.model.TradeEntry;
import fyers.FyersTransactionInfo;

void main() {
    FyersTransactionInfo app = new FyersTransactionInfo();

    // All trades
    List<TradeEntry> allTrades = app.getTradeBook();
    System.out.println("All trades: " + allTrades);

    // Filter by order tag
    List<TradeEntry> taggedTrades = app.getTradeByTag("2:Untagged");
    System.out.println("Trades by tag: " + taggedTrades);
}