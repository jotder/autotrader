import fyers.FyersHoldings;
import com.rj.model.HoldingsSummary;

void main(String[] args) {

    FyersHoldings app = new FyersHoldings();
    HoldingsSummary holdings = app.getHoldings();
    System.out.println(holdings);
}

