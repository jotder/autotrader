import com.rj.model.ReportOrder;
import com.rj.model.ReportTrade;
import fyers.FyersReports;

void main() {
    FyersReports app = new FyersReports();

    // 1. Order history — full current financial year (no params)
    List<ReportOrder> orderHistory = app.getOrderById("id");
    System.out.println("Order History: " + orderHistory);

    // 2. Order history — filtered by symbol and date range
    List<ReportOrder> filtered = app.getOrderByTag("symbol=NSE:SBIN-EQ&from_date=2025-01-01&to_date=2025-01-31");
    System.out.println("Order History (filtered): " + filtered);

    // 3. Trade Book — full current financial year

    List<ReportTrade> tradeHistory = app.getTradeBook();
    System.out.println("Trade History: " + tradeHistory);

    // 4. Trade history — filtered by symbol

    List<ReportTrade> tradeFiltered = app.getTradeByTag("symbol=NSE:IDEA-EQ");
    System.out.println("Trade History (filtered): " + tradeFiltered);
}
