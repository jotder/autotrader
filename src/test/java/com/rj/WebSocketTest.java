package com.rj;

import com.rj.config.ConfigManager;
import com.rj.engine.disruptor.TickDisruptorEngine;
import com.rj.fyers.FyersSocketListener;
import com.tts.in.utilities.Tuple;
import com.tts.in.websocket.FyersSocket;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WebSocketTest {

    public static void main(String[] args) throws Exception {
        TickDisruptorEngine disruptor = new TickDisruptorEngine();
        disruptor.start();

        FyersSocketListener app = new FyersSocketListener(disruptor, null);
        ConfigManager conf = ConfigManager.getInstance();

        app.fyersClass.clientId = conf.getProperty("FYERS_APP_ID");
        app.fyersClass.accessToken = conf.getProperty("ACCESS_TOKEN");

        Tuple<JSONObject, JSONObject> p = app.fyersClass.GetProfile();
        if (p.Item2() != null) {
            System.out.println(p.Item2().toString(6));
        } else if (p.Item1() != null) {
            System.out.println(p.Item1().toString(6));
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

        List<String> symbolsToRemove = var1.subList(0, var1.size() - 1);
        app.unsubscribe(symbolsToRemove);

        app.subscribe(List.of("NSE:AMBUJACEM-EQ", "NSE:BERGEPAINT-EQ", "NSE:COLPAL-EQ"));
        app.subscribe(List.of("NSE:INFY-EQ"));

        Thread.sleep(10000L);
        app.close();
        disruptor.stop();
    }
}
