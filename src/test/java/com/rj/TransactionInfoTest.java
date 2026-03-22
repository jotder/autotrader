import fyers.FyersTransactionInfo;
import com.rj.model.TradeEntry;

void main(String[] args) {
    FyersTransactionInfo app = new FyersTransactionInfo();
    List<TradeEntry> orderBook = app.getTradeBook();
    orderBook.forEach(System.out::println);
}

