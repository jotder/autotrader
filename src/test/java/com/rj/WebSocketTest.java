import com.rj.config.ConfigManager;
import com.tts.in.utilities.Tuple;
import com.tts.in.websocket.FyersSocket;
import fyers.FyersSocketListener;
import org.json.JSONObject;

void main() {
    FyersSocketListener app = new FyersSocketListener();
    ConfigManager conf = ConfigManager.getInstance();

    app.fyersClass.clientId = conf.getProperty("FYERS_APP_ID");
    app.fyersClass.accessToken = conf.getProperty("ACCESS_TOKEN");

    Tuple<JSONObject, JSONObject> p = app.fyersClass.GetProfile();
    // Print formatted JSON object
    if (p.Item2() != null) {
        System.out.println(p.Item2().toString(6)); // Indent with 4 spaces
    } else if (p.Item1() != null) {
        System.out.println(p.Item1().toString(6)); // Fallback in case of error
    } else {
        System.out.println("null");
    }


    app.socket = new FyersSocket(30);
    app.startWebSocket();

    List<String> var1 = Arrays.asList(
            "NSE:AMBUJACEM-EQ", "NSE:BERGEPAINT-EQ", "NSE:COLPAL-EQ",
            "NSE:DABUR-EQ", "NSE:GRASIM-EQ", "NSE:HINDPETRO-EQ",
            "NSE:JSWSTEEL-EQ", "NSE:NTPC-EQ", "NSE:ONGC-EQ", "NSE:SBICARD-EQ"
    );

//    // 1. Subscribe to the list of symbols
//    app.SubscribeData(var1);
//
//    // Wait for 10 seconds to receive data
//    Thread.sleep(30000L);
//
//    // 2. Unsubscribe from all symbols except the last one
    List<String> symbolsToRemove = var1.subList(0, var1.size() - 1);
    app.UnSubscribeData(symbolsToRemove);

//    Thread.sleep(30000L);
    // 3. Subscribe to a new symbol
    app.SubscribeData(List.of("NSE:AMBUJACEM-EQ", "NSE:BERGEPAINT-EQ", "NSE:COLPAL-EQ"));

//    Thread.sleep(30000L);
//
//    // 4. Unsubscribe from the last remaining symbol from the original list
//    app.UnSubscribeData(Collections.singletonList(var1.get(var1.size() - 1)));

//    Thread.sleep(30000L);
//    // 5. Unsubscribe from the newly subscribed symbol
//    app.UnSubscribeData(List.of("NSE:RELIANCE-EQ"));
//
//    Thread.sleep(30000L);
//    // 8. Subscribe to a new symbol
    app.SubscribeData(List.of("NSE:INFY-EQ"));
//
//    Thread.sleep(30000L);
//    // 9. Unsubscribe from the last subscribed symbol
//    app.UnSubscribeData(List.of("NSE:INFY-EQ"));
//    Thread.sleep(30000L);
//
//    // 10. Subscribe to a new symbol
//    app.SubscribeData(List.of("NSE:TCS-EQ"));
//    Thread.sleep(30000L);
//
//    // 11. Unsubscribe from the last subscribed symbol
//    app.UnSubscribeData(List.of("NSE:TCS-EQ"));
//    Thread.sleep(30000L);
//
//    // 12. Subscribe to a new symbol
//    app.SubscribeData(List.of("NSE:WIPRO-EQ"));
//    Thread.sleep(30000L);
//
//    // 13. Unsubscribe from the last subscribed symbol
//    app.UnSubscribeData(List.of("NSE:WIPRO-EQ"));
//    Thread.sleep(30000L);
//
//    // 14. Subscribe to a new symbol
//    app.SubscribeData(List.of("NSE:HCLTECH-EQ"));
//    Thread.sleep(30000L);
//
//    // 15. Unsubscribe from the last subscribed symbol
//    app.UnSubscribeData(List.of("NSE:HCLTECH-EQ"));
//    Thread.sleep(30000L);
//
//    // 16. Subscribe to a new symbol
//    app.SubscribeData(List.of("NSE:LT-EQ"));
//    Thread.sleep(30000L);
//
//    // 17. Unsubscribe from the last subscribed symbol
//    app.UnSubscribeData(List.of("NSE:LT-EQ"));
//    Thread.sleep(30000L);
//
//    // 18. Subscribe to a new symbol
//    app.SubscribeData(List.of("NSE:ULTRACEMCO-EQ"));
//    Thread.sleep(30000L);
//
//    // 19. Unsubscribe from the last subscribed symbol
//    app.UnSubscribeData(List.of("NSE:ULTRACEMCO-EQ"));
//    Thread.sleep(30000L);
//
//    // 20. Subscribe to a new symbol
//    app.SubscribeData(List.of("NSE:BAJAJFINSV-EQ"));
//    Thread.sleep(30000L);
//
//    // 21. Unsubscribe from the last subscribed symbol
//    app.UnSubscribeData(List.of("NSE:BAJAJFINSV-EQ"));
//    Thread.sleep(30000L);
//
//    // Wait for a minute before finishing
//    Thread.sleep(30000L);
//
//    // 22. Subscribe to a new symbol
//    app.SubscribeData(List.of("NSE:BAJFINANCE-EQ"));
//    Thread.sleep(30000L);
//
//    // 23. Unsubscribe from the last subscribed symbol
//    app.UnSubscribeData(List.of("NSE:BAJFINANCE-EQ"));
//    Thread.sleep(30000L);
//
//    // 24. Subscribe to a new symbol
//    app.SubscribeData(List.of("NSE:BHARTIARTL-EQ"));
//    Thread.sleep(30000L);
//
//    // 25. Unsubscribe from the last subscribed symbol
//    app.UnSubscribeData(List.of("NSE:BHARTIARTL-EQ"));
//    Thread.sleep(30000L);
//
//    // 26. Subscribe to a new symbol
//    app.SubscribeData(List.of("NSE:BPCL-EQ"));
//    Thread.sleep(30000L);
//
//    // 27. Unsubscribe from the last subscribed symbol
//    app.UnSubscribeData(List.of("NSE:BPCL-EQ"));
//    app.closeWebSocket();
}