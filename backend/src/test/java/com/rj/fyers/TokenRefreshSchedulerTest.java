package com.rj.fyers;

import com.rj.broker.ITickFeed;
import com.rj.config.ConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenRefreshSchedulerTest {

    @Mock
    private ITickFeed tickFeed;

    @Mock
    private ConfigManager config;

    @Test
    void refreshNow_withNoRefreshToken_returnsFalseAndDoesNotCallAdapter() {
        when(config.getProperty("REFRESH_TOKEN")).thenReturn(null);

        TokenRefreshScheduler scheduler = new TokenRefreshScheduler(config, tickFeed);
        boolean result = scheduler.refreshNow();

        verify(tickFeed, never()).refreshToken(any());
        assert !result;
    }

    @Test
    void refreshNow_withNoPin_returnsFalseAndDoesNotCallAdapter() {
        when(config.getProperty("REFRESH_TOKEN")).thenReturn("some-refresh-token");
        when(config.getProperty("FYERS_PIN")).thenReturn(null);

        TokenRefreshScheduler scheduler = new TokenRefreshScheduler(config, tickFeed);
        boolean result = scheduler.refreshNow();

        verify(tickFeed, never()).refreshToken(any());
        assert !result;
    }

    @Test
    void computeAppHashProducesDeterministicSha256() {
        String hash1 = TokenRefreshScheduler.computeAppHash("myAppId", "mySecret");
        String hash2 = TokenRefreshScheduler.computeAppHash("myAppId", "mySecret");
        assert hash1 != null;
        assert hash1.equals(hash2);
        assert hash1.length() == 64;
    }

    @Test
    void computeAppHashReturnsNullForNullInputs() {
        assert TokenRefreshScheduler.computeAppHash(null, "secret") == null;
        assert TokenRefreshScheduler.computeAppHash("app", null) == null;
    }
}
