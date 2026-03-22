package fyers;

import com.rj.model.OrderEntry;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

public class FyersOrders {

    public List<OrderEntry> getOrders() {
        Tuple<JSONObject, JSONObject> orderList = FyersClientFactory.getConfiguredInstance().GetAllOrders();

        if (orderList.Item2() != null) {
            System.out.println("Orders Error: " + orderList.Item2());
            return Collections.emptyList();
        }

        JSONObject data = orderList.Item1();
        JSONObject ordersJson = data.optJSONObject("data");
        if (ordersJson == null) {
            ordersJson = data;
        }

        return OrderEntry.fromArray(ordersJson.optJSONArray("orderBook"));
    }

    public List<OrderEntry> getOrderById(String orderId) {
        Tuple<JSONObject, JSONObject> tuple = FyersClientFactory.getConfiguredInstance().GetOrderById(orderId);

        if (tuple.Item2() != null) {
            System.out.println("GetOrderById Error: " + tuple.Item2());
            return Collections.emptyList();
        }

        JSONObject data = tuple.Item1();
        JSONObject ordersJson = data.optJSONObject("data");
        if (ordersJson == null) {
            ordersJson = data;
        }

        return OrderEntry.fromArray(ordersJson.optJSONArray("orderBook"));
    }
}