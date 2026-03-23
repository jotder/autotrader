package fyers;

import com.rj.model.MultiOrderResult;
import com.rj.model.OrderResult;
import com.tts.in.model.FyersClass;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

import java.util.List;

public class FyersOrderManagement {
    FyersClass fyersClass;

    public FyersOrderManagement() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    public OrderResult modifyOrder(PlaceOrderModel model) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.ModifyOrder(model);
        if (tuple.Item2() != null) {
            System.out.println("ModifyOrder Error: " + tuple.Item2());
            return null;
        }
        return OrderResult.from(tuple.Item1());
    }

    public MultiOrderResult modifyMultipleOrders(List<PlaceOrderModel> models) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.ModifyMultipleOrders(models);
        if (tuple.Item2() != null) {
            System.out.println("ModifyMultipleOrders Error: " + tuple.Item2());
            return null;
        }
        return MultiOrderResult.from(tuple.Item1());
    }

    public OrderResult cancelOrder(String orderId) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.CancelOrder(orderId);
        if (tuple.Item2() != null) {
            System.out.println("CancelOrder Error: " + tuple.Item2());
            return null;
        }
        return OrderResult.from(tuple.Item1());
    }

    public MultiOrderResult cancelMultipleOrders(List<String> orderIds) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.CancelMultipleOrders(orderIds);
        if (tuple.Item2() != null) {
            System.out.println("CancelMultipleOrders Error: " + tuple.Item2());
            return null;
        }
        return MultiOrderResult.from(tuple.Item1());
    }
}
