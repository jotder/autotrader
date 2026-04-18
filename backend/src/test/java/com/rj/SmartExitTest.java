import com.rj.model.SmartExitTrigger;
import com.rj.fyers.FyersSmartExit;
import org.json.JSONObject;

void main() {
    FyersSmartExit app = new FyersSmartExit();

    // 1. Get all Smart Exit triggers
    List<SmartExitTrigger> triggers = app.getSmartExits("");
    System.out.println("Smart Exit Triggers: " + triggers);

    // 2. Create a Smart Exit
    JSONObject createReq = new JSONObject();
    createReq.put("name", "Alert Only Strategy");
    createReq.put("type", 1);
    createReq.put("profitRate", 5000);
    createReq.put("lossRate", -2000);
    System.out.println("Create: " + app.createSmartExit(createReq));

    // 3. Update a Smart Exit
    JSONObject updateReq = new JSONObject();
    updateReq.put("flowId", "2ac656b4-d11c-4013-9e90-04ce7f9dc273");
    updateReq.put("profitRate", 80050);
    updateReq.put("lossRate", -50);
    updateReq.put("type", 1);
    updateReq.put("name", "test");
    System.out.println("Update: " + app.updateSmartExit(updateReq));

    // 4. Activate / Deactivate
    JSONObject activateReq = new JSONObject();
    activateReq.put("flowId", "2ac656b4-d11c-4013-9e90-04ce7f9dc273");
    System.out.println("Activate/Deactivate: " + app.activateDeactivateSmartExit(activateReq));
}
