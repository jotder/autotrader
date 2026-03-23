import com.rj.model.MarketStatus;
import fyers.FyersBrokerConfig;

void main() {
    FyersBrokerConfig app = new FyersBrokerConfig();
    MarketStatus status = app.getMarketStatus();
    System.out.println("Market Status: " + status.toString());
}
