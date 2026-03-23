package fyers;

import com.rj.model.GTTOrderBook;
import com.rj.model.OrderResult;
import com.tts.in.model.FyersClass;
import com.tts.in.model.GTTModel;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

import java.util.List;

public class FyersGTT {
    FyersClass fyersClass;

    public FyersGTT() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    public OrderResult placeGTTOrder(List<GTTModel> models) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.PlaceGTTOrder(models);
        if (tuple.Item2() != null) {
            System.out.println("PlaceGTTOrder Error: " + tuple.Item2());
            return null;
        }
        return OrderResult.from(tuple.Item1());
    }

    public OrderResult modifyGTTOrder(List<GTTModel> models) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.ModifyGTTOrder(models);
        if (tuple.Item2() != null) {
            System.out.println("ModifyGTTOrder Error: " + tuple.Item2());
            return null;
        }
        return OrderResult.from(tuple.Item1());
    }

    public OrderResult cancelGTTOrder(String orderId) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.CancelGTTOrder(orderId);
        if (tuple.Item2() != null) {
            System.out.println("CancelGTTOrder Error: " + tuple.Item2());
            return null;
        }
        return OrderResult.from(tuple.Item1());
    }

    public GTTOrderBook getGTTOrders() {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetGTTOrderBook();
        if (tuple.Item2() != null) {
            System.out.println("GetGTTOrders Error: " + tuple.Item2());
            return null;
        }
        return GTTOrderBook.from(tuple.Item1());
    }
}
