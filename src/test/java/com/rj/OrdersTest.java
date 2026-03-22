
import fyers.FyersOrders;
import com.rj.model.OrderEntry;

import java.util.List;

void main() {
    FyersOrders app = new FyersOrders();

    // 1. All orders for today
    List<OrderEntry> orders = app.getOrders();
    System.out.println("All Orders: " + orders);

    // 2. Filter by a specific order id
     List<OrderEntry> byId = app.getOrderById("24092500183464");
     System.out.println("Order By Id: " + byId);
}
