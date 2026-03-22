
import fyers.FyersOrderManagement;
import com.rj.model.MultiOrderResult;
import com.rj.model.OrderResult;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.OrderType;
import com.tts.in.utilities.TransactionType;

import java.util.List;

void main() {
    FyersOrderManagement app = new FyersOrderManagement();

    // 1. Modify a single pending order (replace orderId with a real pending order)
     PlaceOrderModel mod = new PlaceOrderModel();
     mod.OrderId    = "24092500338700";
     mod.Qty        = 1;
     mod.OrderType  = OrderType.MarketOrder.getDescription();
     mod.LimitPrice = 0;
     mod.StopPrice  = 0;
    OrderResult modified = app.modifyOrder(mod);
     System.out.println("Modified Order: " + modified);

    // 2. Modify multiple orders
     PlaceOrderModel m1 = new PlaceOrderModel();
     m1.OrderId   = "24092700252396"; m1.Qty = 1;
     m1.OrderType = OrderType.LimitOrder.getDescription();
     m1.Side      = TransactionType.Buy.getValue(); m1.LimitPrice = 0;
     PlaceOrderModel m2 = new PlaceOrderModel();
     m2.OrderId   = "24092700251935"; m2.Qty = 1;
     m2.OrderType = OrderType.LimitOrder.getDescription();
     m2.Side      = TransactionType.Buy.getValue(); m2.LimitPrice = 0;
    MultiOrderResult multiMod = app.modifyMultipleOrders(List.of(m1, m2));
     System.out.println("Multi Modify: " + multiMod);

    // 3. Cancel a single pending order
    OrderResult cancelled = app.cancelOrder("24092500390860");
     System.out.println("Cancelled: " + cancelled);

    // 4. Cancel multiple orders
    MultiOrderResult multiCancel = app.cancelMultipleOrders(List.of("24092500390490", "24092500389713"));
     System.out.println("Multi Cancel: " + multiCancel);

    System.out.println("OrderManagement test — uncomment the block you want to run.");
}
