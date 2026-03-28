package com.rj.fyers;

import com.rj.engine.disruptor.TickDisruptorEngine;
import com.rj.engine.OrderManager;
import com.rj.model.Tick;
import com.tts.in.model.FyersClass;
import com.tts.in.websocket.FyersSocket;
import com.tts.in.websocket.FyersSocketDelegate;
import in.tts.hsjavalib.ChannelModes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread 1 — WebSocket Feed.
 * Listens to Fyers real-time ticks and publishes them to the LMAX Disruptor ring buffer.
 */
@Component
public class FyersSocketListener implements FyersSocketDelegate {
    private static final Logger log = LoggerFactory.getLogger(FyersSocketListener.class);
    
    public FyersSocket socket;
    public final FyersClass fyersClass = FyersClass.getInstance();
    
    private final TickDisruptorEngine disruptor;
    private final OrderManager orderManager;

    public FyersSocketListener(TickDisruptorEngine disruptor, @Lazy OrderManager orderManager) {
        this.disruptor = disruptor;
        this.orderManager = orderManager;
    }

    public void startWebSocket() {
        if (this.socket == null) {
            log.error("Cannot start WebSocket: socket object is null");
            return;
        }
        this.socket.webSocketDelegate = this;
        this.socket.ConnectHSM(ChannelModes.FULL);
        this.socket.setSymbolsInResponse(true);

        log.info("Starting WebSocket connection to Fyers...");
    }

    public void subscribe(List<String> symbols) {
        log.info("Subscribing to: {}", symbols);
        if (this.socket != null) {
            this.socket.SubscribeData(new ArrayList<>(symbols));
        }
    }

    public void unsubscribe(List<String> symbols) {
        log.info("Unsubscribing from: {}", symbols);
        if (this.socket != null) {
            this.socket.UnSubscribeData(new ArrayList<>(symbols));
        }
    }

    public void close() {
        if (this.socket != null) {
            this.socket.Close();
            log.info("WebSocket connection closed.");
        }
    }

    public void OnScrips(JSONObject scrips) {
        try {
            Tick tick = Tick.from(scrips);
            disruptor.publish(tick);
        } catch (Exception e) {
            log.error("Error processing tick scrip: {}", e.getMessage(), e);
        }
    }

    public void OnIndex(JSONObject index) { log.debug("On Index: {}", index); }
    public void OnDepth(JSONObject depths) { log.trace("On Depth: {}", depths); }
    
    public void OnOrder(JSONObject orders) { 
        log.info("On Order Update: {}", orders);
        if (orderManager != null) {
            orderManager.processBrokerUpdate(orders);
        }
    }
    
    public void OnTrade(JSONObject trades) { log.info("On Trade Update: {}", trades); }
    public void OnPosition(JSONObject positions) { log.info("On Position Update: {}", positions); }
    public void OnOpen(String status) { log.info("WebSocket Opened: {}", status); }
    public void OnClose(String status) { log.warn("WebSocket Closed: {}", status); }
    public void OnError(JSONObject error) { log.error("WebSocket Error: {}", error); }
    public void OnReconnect(JSONObject res) { log.info("WebSocket Reconnecting: {}", res); }
    public void OnMessage(JSONObject message) { log.debug("On Message: {}", message); }
}
