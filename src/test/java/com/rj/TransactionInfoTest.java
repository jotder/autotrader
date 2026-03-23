import com.rj.model.TradeEntry;
import fyers.FyersTransactionInfo;

void main(String[] args) {
    FyersTransactionInfo app = new FyersTransactionInfo();
    List<TradeEntry> orderBook = app.getTradeBook();
    orderBook.forEach(System.out::println);
}

