
import fyers.FyersBrokerConfig;
import com.rj.model.MarketStatus;

void main() {
    FyersBrokerConfig app = new FyersBrokerConfig();
    MarketStatus status = app.getMarketStatus();
    System.out.println("Market Status: " + status);
}
