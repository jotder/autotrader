package fyers;

import com.rj.model.ReportOrder;
import com.rj.model.ReportTrade;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

import java.util.List;

public class FyersReports {

    /** @param queryParams e.g. "symbol=NSE:SBIN-EQ&from_date=2025-01-01&to_date=2025-01-31", or null for defaults */
    public List<ReportOrder> getOrderById(String queryParams) {
        Tuple<JSONObject, JSONObject> tuple = FyersClientFactory.getConfiguredInstance().GetOrderById(queryParams);
        if (tuple.Item2() != null) {
            System.out.println("GetOrderById Error: " + tuple.Item2());
            return null;
        }
        return ReportOrder.listFrom(tuple.Item1());
    }

    public List<ReportOrder> getOrderByTag(String queryParams) {
        Tuple<JSONObject, JSONObject> tuple = FyersClientFactory.getConfiguredInstance().GetOrderByTag(queryParams);
        if (tuple.Item2() != null) {
            System.out.println("GetOrderByTag Error: " + tuple.Item2());
            return null;
        }
        return ReportOrder.listFrom(tuple.Item1());
    }

    /** @param queryParams e.g. "symbol=NSE:IDEA-EQ&from_date=2025-01-01&to_date=2025-01-31", or null for defaults */
    public List<ReportTrade> getTradeByTag(String queryParams) {
        Tuple<JSONObject, JSONObject> tuple = FyersClientFactory.getConfiguredInstance().GetTradeByTag(queryParams);
        if (tuple.Item2() != null) {
            System.out.println("GetTradeByTag Error: " + tuple.Item2());
            return null;
        }
        return ReportTrade.listFrom(tuple.Item1());
    }

    public List<ReportTrade> getTradeBook() {
        Tuple<JSONObject, JSONObject> tuple = FyersClientFactory.getConfiguredInstance().GetTradeBook();
        if (tuple.Item2() != null) {
            System.out.println("GetTradeBook Error: " + tuple.Item2());
            return null;
        }
        return ReportTrade.listFrom(tuple.Item1());
    }
}
