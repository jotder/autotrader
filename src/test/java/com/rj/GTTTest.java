
import fyers.FyersGTT;
import com.rj.model.GTTOrderBook;
import com.rj.model.OrderResult;
import com.tts.in.model.GTTLeg;
import com.tts.in.model.GTTModel;

import java.util.List;

void main() {
    FyersGTT app = new FyersGTT();

    // 1. Get GTT order book
    GTTOrderBook book = app.getGTTOrders();
    System.out.println("GTT Order Book: " + book);

    // 2. Place a GTT Single order
     GTTModel single = new GTTModel();
     single.Side        = 1;
     single.Symbol      = "NSE:IDFCFIRSTB-EQ";
     single.productType = "MTF";
     single.addGTTLeg("leg1", new GTTLeg(400, 400, 1));
    OrderResult placed = app.placeGTTOrder(List.of(single));
     System.out.println("GTT Single Placed: " + placed);

    // 3. Place a GTT OCO (target + stop loss)
     GTTModel oco = new GTTModel();
     oco.Side        = 1;
     oco.Symbol      = "NSE:IDFCFIRSTB-EQ";
     oco.productType = "MTF";
     oco.addGTTLeg("leg1", new GTTLeg(400, 400, 1));
     oco.addGTTLeg("leg2", new GTTLeg( 50,  50, 1));
    OrderResult ocoPlaced = app.placeGTTOrder(List.of(oco));
     System.out.println("GTT OCO Placed: " + ocoPlaced);

    // 4. Modify a GTT order
     GTTModel mod = new GTTModel();
     mod.Id = "25012300000741";
     mod.addGTTLeg("leg1", new GTTLeg(1100, 1100, 5));
     mod.addGTTLeg("leg2", new GTTLeg(  67,   67, 5));
    OrderResult modified = app.modifyGTTOrder(List.of(mod));
     System.out.println("GTT Modified: " + modified);

    // 5. Cancel a GTT order
    OrderResult cancelled = app.cancelGTTOrder("25012200002259");
     System.out.println("GTT Cancelled: " + cancelled);
}
