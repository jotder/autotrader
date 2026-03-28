import com.rj.model.OrderEntry;
import com.rj.fyers.FyersOrders;

void main() {
    FyersOrders app = new FyersOrders();

    // 1. All orders for today
    List<OrderEntry> orders = app.getOrders();
    System.out.println("All Orders: " + orders);

    // 2. Filter by a specific order id
    List<OrderEntry> byId = app.getOrderById("??/");
    System.out.println("Order By Id: " + byId);
}
