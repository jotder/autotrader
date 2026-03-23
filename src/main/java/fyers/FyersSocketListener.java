package fyers;

import com.rj.model.Tick;
import com.rj.model.TickStore;
import com.tts.in.model.FyersClass;
import com.tts.in.websocket.FyersSocket;
import com.tts.in.websocket.FyersSocketDelegate;
import in.tts.hsjavalib.ChannelModes;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FyersSocketListener implements FyersSocketDelegate {
    private static Logger logger = LogManager.getLogger(FyersSocketListener.class.getName());
    public FyersSocket socket;
    public FyersClass fyersClass = FyersClass.getInstance();
    public int c = 0;

    // Track if header is printed
    private boolean isHeaderPrinted = false;

    public void startWebSocket() {
        this.socket.webSocketDelegate = this;
        this.socket.ConnectHSM(ChannelModes.FULL);
        this.socket.setSymbolsInResponse(true);

        System.out.println("Starting WebSocket for " + FyersSocketListener.class.getName());
    }

    public void SubscribeData(List<String> list) {
        System.out.println(": Subscribing " + list);
        if (this.socket != null) {
            this.socket.SubscribeData(new ArrayList<>(list));
        }
    }

    public void UnSubscribeData(List<String> list) {
        System.out.println(": Unsubscribing " + list);
        if (this.socket != null) {
            this.socket.UnSubscribeData(new ArrayList<>(list));
        }
    }

    public void closeWebSocket() {
//        if (this.socket != null) {
//            this.socket.Close();
//        }
    }

    public void OnIndex(JSONObject index) {
        System.out.println(": On Index: " + index);
    }

    public void OnScrips(JSONObject scrips) {
        Tick tick = Tick.from(scrips);

        // Thread 1 → Thread 2 handoff: publish tick to the in-memory store.
        // CandleService workers snapshot this store every candle boundary.
        TickStore.getInstance().append(tick);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
        String feedTimeStr = formatter.format(tick.getFeedTime());
        String lastTradeTimeStr = formatter.format(tick.getLastTradedTime());

        if (!isHeaderPrinted) {
            System.out.printf("%-10s | %-10s | %-18s | %-4s | %-8s | %-8s | %-8s | %-8s | %-8s | %-10s | %-16s | %-10s | %-10s | %-10s | %-10s | %-10s | %-12s | %-15s | %-15s | %-12s | %-12s%n",
                    "FeedTime", "LTT", "Symbol", "Type", "LTP", "Open", "High", "Low", "PrevCls", "AvgPrice", "Change", "Volume", "LTP Qty", "TotBuy", "TotSell", "OI", "Turnover", "Bid", "Ask", "Circ(U/L)", "52W(H/L)");
            System.out.println("-".repeat(260));
            isHeaderPrinted = true;
        }

        String changeStr = String.format("%+.2f (%.2f%%)", tick.getChange(), tick.getChangePct());
        String bidStr = String.format("%.2f x %d", tick.getBidPrice(), tick.getBidSize());
        String askStr = String.format("%.2f x %d", tick.getAskPrice(), tick.getAskSize());
        String circuits = String.format("%d / %d", tick.getUpperCircuit(), tick.getLowerCircuit());
        String yearHL = String.format("%d / %d", tick.getYearHigh(), tick.getYearLow());

        System.out.printf("%-10s | %-10s | %-18s | %-4s | %-8.2f | %-8.2f | %-8.2f | %-8.2f | %-8.2f | %-10.2f | %-16s | %-10d | %-10d | %-10d | %-10d | %-10d | %-12d | %-15s | %-15s | %-12s | %-12s%n",
                feedTimeStr, lastTradeTimeStr, tick.getSymbol(), tick.getType(), tick.getLtp(), tick.getOpen(), tick.getHigh(), tick.getLow(), tick.getPrevClose(),
                tick.getAvgTradePrice(), changeStr, tick.getVolTradedToday(), tick.getLastTradedQty(),
                tick.getTotBuyQty(), tick.getTotSellQty(), tick.getOpenInterest(), tick.getTurnover(),
                bidStr, askStr, circuits, yearHL);
    }

    public void OnDepth(JSONObject depths) {
        System.out.println(": On Depth: " + depths);
    }

    public void OnOrder(JSONObject orders) {
        System.out.println(": On Orders: " + orders);
    }

    public void OnTrade(JSONObject trades) {
        System.out.println(": On Trades: " + trades);
    }

    public void OnPosition(JSONObject positions) {
        System.out.println(": On Positions: " + positions);
    }

    public void OnOpen(String status) {
        System.out.println(": On open: " + status);
    }

    public void OnClose(String status) {
        System.out.println(": *** On Close: " + status);
    }

    public void OnError(JSONObject error) {
        System.out.println(": *** On Error: " + error);
        System.out.println(": *** On Error: " + error);
    }

    public void OnReconnect(JSONObject var1) {
        System.out.println(": *** On Reconnect: " + var1);
        System.out.println(": *** On Reconnect: " + var1);
    }

    public void OnMessage(JSONObject message) {
        System.out.println(": On Message: " + message);
    }
}
