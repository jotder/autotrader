package com.rj.broker;

import java.util.List;

public interface ITickFeed {
    void connect(String accessToken);
    void disconnect();
    void subscribe(List<String> symbols);
    void unsubscribe(List<String> symbols);
    boolean isConnected();
    void refreshToken(String newToken);
}
