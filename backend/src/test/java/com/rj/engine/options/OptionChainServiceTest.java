package com.rj.engine.options;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rj.broker.IMarketDataAdapter;
import com.rj.config.OptionChainConfig;
import com.rj.config.SymbolMasterCache;
import com.rj.model.OptionChainResult;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OptionChainServiceTest {

    @Mock IMarketDataAdapter marketDataAdapter;
    @Mock SymbolMasterCache symbolMasterCache;

    private OptionChainConfig config;
    private OptionChainService service;
    private OptionChainResult fakeResult;

    @BeforeEach
    void setUp() {
        config = new OptionChainConfig();
        // Use a minimal result with non-null optionsChain and expiryData
        JSONObject json = new JSONObject();
        JSONObject data = new JSONObject();
        data.put("callOi", 1000L);
        data.put("putOi", 800L);
        data.put("optionsChain", new org.json.JSONArray());
        data.put("expiryData", new org.json.JSONArray());
        json.put("data", data);
        fakeResult = OptionChainResult.from(json);

        when(marketDataAdapter.getOptionChain(eq("NSE:NIFTY50-INDEX"), anyInt(), anyString()))
                .thenReturn(fakeResult);

        service = new OptionChainService(marketDataAdapter, config,
                symbolMasterCache, new ObjectMapper());
    }

    @Test
    void getChain_coldStart_fetchesAndCaches() {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));

        Optional<OptionChainSnapshot> result = service.getChain("NSE:NIFTY50-INDEX");

        assertThat(result).isPresent();
        assertThat(result.get().underlying()).isEqualTo("NSE:NIFTY50-INDEX");
        assertThat(result.get().data()).isSameAs(fakeResult);
        verify(marketDataAdapter, times(1))
                .getOptionChain(eq("NSE:NIFTY50-INDEX"), anyInt(), anyString());
    }

    @Test
    void getChain_secondCall_returnsCachedWithoutRefetch() {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));
        service.getChain("NSE:NIFTY50-INDEX"); // populate cache

        service.getChain("NSE:NIFTY50-INDEX"); // second call

        verify(marketDataAdapter, times(1))
                .getOptionChain(any(), anyInt(), anyString()); // still only 1 fetch
    }

    @Test
    void getChain_brokerThrows_returnsEmpty() {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));
        when(marketDataAdapter.getOptionChain(any(), anyInt(), any()))
                .thenThrow(new RuntimeException("API down"));

        Optional<OptionChainSnapshot> result = service.getChain("NSE:NIFTY50-INDEX");

        assertThat(result).isEmpty();
    }

    @Test
    void getChain_unknownUnderlying_returnsEmpty() {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));

        Optional<OptionChainSnapshot> result = service.getChain("NSE:UNKNOWN");

        assertThat(result).isEmpty();
        verifyNoInteractions(marketDataAdapter);
    }

    @Test
    void refresh_debounce_onlyOneFetchInFlight() throws InterruptedException {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));
        // Slow down the mock so in-flight flag stays set
        when(marketDataAdapter.getOptionChain(any(), anyInt(), any()))
                .thenAnswer(inv -> { Thread.sleep(50); return fakeResult; });

        service.refresh("NSE:NIFTY50-INDEX");
        service.refresh("NSE:NIFTY50-INDEX"); // second call while first in-flight

        Thread.sleep(200); // wait for async fetch to complete
        verify(marketDataAdapter, times(1))
                .getOptionChain(any(), anyInt(), any()); // debounced — second call dropped
    }

    @Test
    void refreshIfSignificant_highConfidence_triggersRefresh() throws InterruptedException {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));

        service.refreshIfSignificant("NSE:NIFTY50-INDEX",
                com.rj.model.Signal.BUY, 0.90, 1.2); // high confidence → should trigger

        Thread.sleep(200);
        verify(marketDataAdapter, atLeastOnce())
                .getOptionChain(eq("NSE:NIFTY50-INDEX"), anyInt(), any());
    }

    @Test
    void refreshIfSignificant_lowConfidenceLowVol_noTrigger() throws InterruptedException {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));

        service.refreshIfSignificant("NSE:NIFTY50-INDEX",
                com.rj.model.Signal.HOLD, 0.50, 0.8); // weak signal → no trigger

        Thread.sleep(100);
        verifyNoInteractions(marketDataAdapter);
    }

    @Test
    void allSnapshots_returnsAllCachedEntries() {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));
        service.getChain("NSE:NIFTY50-INDEX");

        assertThat(service.allSnapshots()).containsKey("NSE:NIFTY50-INDEX");
    }
}
