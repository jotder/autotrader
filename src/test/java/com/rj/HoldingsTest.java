import com.rj.model.HoldingsSummary;
import fyers.FyersHoldings;

void main(String[] args) {

    FyersHoldings app = new FyersHoldings();
    HoldingsSummary holdings = app.getHoldings();
    System.out.println(holdings);
}

