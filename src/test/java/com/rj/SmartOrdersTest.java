import com.rj.model.SmartOrderBook;
import fyers.FyersSmartOrders;
import org.json.JSONObject;

void main() {
    FyersSmartOrders app = new FyersSmartOrders();

    // 1. Smart Order Book (all)
    SmartOrderBook book = app.getSmartOrderBook("");
    System.out.println("Smart Order Book: " + book);

    // 2. Smart Limit Order
    JSONObject limitReq = new JSONObject();
    limitReq.put("symbol", "NSE:SBIN-EQ");
    limitReq.put("side", 1);
    limitReq.put("qty", 1);
    limitReq.put("productType", "CNC");
    limitReq.put("limitPrice", 1250);
    limitReq.put("stopPrice", 1200);
    limitReq.put("orderType", 1);
    limitReq.put("endTime", 1769162100);
    limitReq.put("hpr", 1300);
    limitReq.put("lpr", 700);
    limitReq.put("mpp", 1);
    limitReq.put("onExp", 2);
    System.out.println("Smart Limit: " + app.createSmartLimit(limitReq));

    // 3. Smart Trail (TSL)
    JSONObject trailReq = new JSONObject();
    trailReq.put("symbol", "NSE:SBIN-EQ");
    trailReq.put("side", 1);
    trailReq.put("qty", 1);
    trailReq.put("productType", "CNC");
    trailReq.put("stopPrice", 740);
    trailReq.put("jump_diff", 5);
    trailReq.put("orderType", 2);
    trailReq.put("mpp", 1);
    System.out.println("Smart Trail: " + app.createSmartTrail(trailReq));

    // 4. Smart Step (averaging)
    JSONObject stepReq = new JSONObject();
    stepReq.put("symbol", "NSE:SBIN-EQ");
    stepReq.put("side", 1);
    stepReq.put("qty", 10);
    stepReq.put("productType", "CNC");
    stepReq.put("initQty", 2);
    stepReq.put("avgqty", 2);
    stepReq.put("avgdiff", 5);
    stepReq.put("direction", 1);
    stepReq.put("limitPrice", 750);
    stepReq.put("orderType", 1);
    stepReq.put("startTime", 1769149800);
    stepReq.put("endTime", 1769162100);
    stepReq.put("hpr", 800);
    stepReq.put("lpr", 700);
    stepReq.put("mpp", 1);
    System.out.println("Smart Step: " + app.createSmartStep(stepReq));

    // 5. Smart SIP
    JSONObject sipReq = new JSONObject();
    sipReq.put("symbol", "NSE:SBIN-EQ");
    sipReq.put("side", 1);
    sipReq.put("amount", 5000);
    sipReq.put("productType", "CNC");
    sipReq.put("freq", 3);
    sipReq.put("sip_day", 15);
    sipReq.put("imd_start", false);
    sipReq.put("endTime", 1772512200);
    sipReq.put("hpr", 900);
    sipReq.put("lpr", 600);
    sipReq.put("step_up_freq", 3);
    sipReq.put("step_up_amount", 500);
    System.out.println("Smart SIP: " + app.createSmartSip(sipReq));

    // 6. Pause / Resume / Modify / Cancel (use flowId from order book)
    String flowId = "d99419e1-42c4-4944-b9f6-5399b68cd686";
    System.out.println("Pause:  " + app.pauseSmartOrder(new JSONObject().put("flowId", flowId)));
    JSONObject modReq = new JSONObject();
    modReq.put("flowId", flowId);
    modReq.put("qty", 5);
    modReq.put("endTime", 1769162100);
    System.out.println("Modify: " + app.modifySmartOrder(modReq));
    System.out.println("Resume: " + app.resumeSmartOrder(new JSONObject().put("flowId", flowId)));
    System.out.println("Cancel: " + app.cancelSmartOrder(new JSONObject().put("flowId", "d40d8597-26da-434b-879c-0de3b8492cda")));
}
