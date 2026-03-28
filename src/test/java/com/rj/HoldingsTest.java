import com.rj.model.HoldingsSummary;
import com.rj.fyers.FyersHoldings;

void main(String[] args) {

    FyersHoldings app = new FyersHoldings();
    HoldingsSummary holdings = app.getHoldings();
    System.out.println(holdings);
}

