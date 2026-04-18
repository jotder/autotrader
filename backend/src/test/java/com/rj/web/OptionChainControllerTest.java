package com.rj.web;

import com.rj.config.OptionChainConfig;
import com.rj.engine.options.OptionChainService;
import com.rj.engine.options.OptionChainSnapshot;
import com.rj.model.OptionChainResult;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OptionChainController.class)
class OptionChainControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean OptionChainService optionChainService;
    @MockBean OptionChainConfig optionChainConfig;

    private OptionChainSnapshot fakeSnapshot(String underlying) {
        JSONObject json = new JSONObject();
        JSONObject data = new JSONObject();
        data.put("callOi", 1000L);
        data.put("putOi", 800L);
        data.put("optionsChain", new org.json.JSONArray());
        data.put("expiryData", new org.json.JSONArray());
        json.put("data", data);
        return new OptionChainSnapshot(underlying, OptionChainResult.from(json), Instant.now());
    }

    @Test
    void getChain_found_returns200() throws Exception {
        when(optionChainService.getChain("NSE:NIFTY50-INDEX"))
                .thenReturn(Optional.of(fakeSnapshot("NSE:NIFTY50-INDEX")));
        when(optionChainConfig.getStaleThresholdSeconds()).thenReturn(90);

        mockMvc.perform(get("/api/option-chain/{u}", "NSE:NIFTY50-INDEX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.underlying").value("NSE:NIFTY50-INDEX"))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    void getChain_notFound_returns404() throws Exception {
        when(optionChainService.getChain(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/option-chain/{u}", "NSE:UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getChain_withExpiryFilter_returns200() throws Exception {
        when(optionChainService.getChain("NSE:NIFTY50-INDEX"))
                .thenReturn(Optional.of(fakeSnapshot("NSE:NIFTY50-INDEX")));
        when(optionChainConfig.getStaleThresholdSeconds()).thenReturn(90);

        mockMvc.perform(get("/api/option-chain/{u}", "NSE:NIFTY50-INDEX")
                        .param("expiry", "10-Apr-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.underlying").value("NSE:NIFTY50-INDEX"));
    }

    @Test
    void refresh_returns202() throws Exception {
        mockMvc.perform(post("/api/option-chain/{u}/refresh", "NSE:NIFTY50-INDEX"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.underlying").value("NSE:NIFTY50-INDEX"));

        verify(optionChainService).refresh("NSE:NIFTY50-INDEX");
    }

    @Test
    void summary_returnsListWithPcr() throws Exception {
        OptionChainSnapshot snap = fakeSnapshot("NSE:NIFTY50-INDEX");
        when(optionChainService.allSnapshots()).thenReturn(Map.of("NSE:NIFTY50-INDEX", snap));
        when(optionChainConfig.getStaleThresholdSeconds()).thenReturn(90);

        mockMvc.perform(get("/api/option-chain/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].underlying").value("NSE:NIFTY50-INDEX"))
                .andExpect(jsonPath("$[0].pcr").exists())
                .andExpect(jsonPath("$[0].stale").value(false));
    }
}
