package com.rj.web;

import com.rj.engine.CandleDatabase;
import com.rj.engine.DownloadTracker;
import com.rj.engine.SymbolProfiler;
import com.rj.model.Candle;
import com.rj.model.SymbolProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandleController.class)
class CandleControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean CandleDatabase candleDatabase;
    @MockBean DownloadTracker downloadTracker;
    @MockBean SymbolProfiler symbolProfiler;

    @Test
    void candleDbSymbols_returnsSet() throws Exception {
        when(candleDatabase.availableSymbols()).thenReturn(Set.of("NSE:SBIN-EQ"));
        mockMvc.perform(get("/api/candle-db/symbols"))
                .andExpect(status().isOk());
    }

    @Test
    void candleDbLoad_notFound_returns404() throws Exception {
        when(candleDatabase.load(eq("NSE:SBIN-EQ"), any(LocalDate.class))).thenReturn(List.of());
        mockMvc.perform(get("/api/candle-db/NSE:SBIN-EQ").param("date", "2026-01-02"))
                .andExpect(status().isNotFound());
    }

    @Test
    void candleDbLoad_found_returnsCandles() throws Exception {
        Candle c = mock(Candle.class);
        when(candleDatabase.load(eq("NSE:SBIN-EQ"), any(LocalDate.class))).thenReturn(List.of(c));
        mockMvc.perform(get("/api/candle-db/NSE:SBIN-EQ").param("date", "2026-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void download_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/candle-db/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadStatus_notFound_returns404() throws Exception {
        when(downloadTracker.getJob("unknown-job")).thenReturn(null);
        mockMvc.perform(get("/api/candle-db/download/unknown-job"))
                .andExpect(status().isNotFound());
    }

    @Test
    void profile_noData_returns400() throws Exception {
        when(symbolProfiler.profile(eq("NSE:SBIN-EQ"), any(), any())).thenReturn(null);
        mockMvc.perform(get("/api/profile/NSE:SBIN-EQ")
                        .param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profile_found_returns200() throws Exception {
        SymbolProfile profile = mock(SymbolProfile.class);
        when(symbolProfiler.profile(eq("NSE:SBIN-EQ"), any(), any())).thenReturn(profile);
        mockMvc.perform(get("/api/profile/NSE:SBIN-EQ")
                        .param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isOk());
    }
}
