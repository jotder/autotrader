import com.rj.model.MultiOrderResult;
import com.rj.model.OrderResult;
import com.tts.in.model.Leg;
import com.tts.in.model.MultiLegModel;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.ProductType;
import com.tts.in.utilities.TransactionType;
import com.rj.fyers.FyersOrderPlacement;

void main() {
    FyersOrderPlacement app = new FyersOrderPlacement();

    // 1. Single market order (CNC buy)
    PlaceOrderModel order = FyersOrderPlacement.marketOrder(
            "NSE:IDEA-EQ", 1, TransactionType.Buy.getValue(), ProductType.CNC);
    order.OrderTag = "PlacingOrderWithTag2";
    OrderResult placed = app.placeOrder(order);
    System.out.println("Single Order: " + placed);

    // 2. Multiple orders at once (up to 10)
    PlaceOrderModel o1 = FyersOrderPlacement.marketOrder("NSE:PSB-EQ", 1, TransactionType.Buy.getValue(), ProductType.INTRADAY);
    o1.OrderTag = "ManualOrderTag1";
    PlaceOrderModel o2 = FyersOrderPlacement.marketOrder("NSE:IDEA-EQ", 1, TransactionType.Buy.getValue(), ProductType.CNC);
    o2.OrderTag = "tag1";
    PlaceOrderModel o3 = FyersOrderPlacement.marketOrder("NSE:UCOBANK-EQ", 1, TransactionType.Sell.getValue(), ProductType.INTRADAY);
    o3.OrderTag = "tag2";
    MultiOrderResult multi = app.placeMultipleOrders(List.of(o1, o2, o3));
    System.out.println("Multi Orders: " + multi);

    // 3. MultiLeg order (3-legged spread)
    MultiLegModel ml = new MultiLegModel();
    ml.OrderTag = "tag1";
    ml.ProductType = ProductType.MARGIN;
    ml.OfflineOrder = false;
    ml.OrderType = "3L";
    ml.Validity = "IOC";
    ml.addLeg("leg1", new Leg("NSE:SBIN24JUNFUT", 750, 1, 1, 800));
    ml.addLeg("leg2", new Leg("NSE:SBIN24JULFUT", 750, 1, 1, 790));
    ml.addLeg("leg3", new Leg("NSE:SBIN24JUN900CE", 750, 1, 1, 3));
    MultiOrderResult multiLeg = app.placeMultiLegOrder(List.of(ml));
    System.out.println("MultiLeg Order: " + multiLeg);
}
