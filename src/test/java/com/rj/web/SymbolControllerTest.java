package com.rj.web;

import com.rj.config.*;
import com.rj.model.dim.SymbolMasterEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SymbolController.class)
class SymbolControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ConfigManager configManager;
    @MockBean DimensionDataCache dimensionCache;
    @MockBean SymbolMasterCache symbolMasterCache;

    @Test
    void symbols_returnsRegistryContents() throws Exception {
        SymbolRegistry registry = mock(SymbolRegistry.class);
        when(configManager.getSymbolRegistry()).thenReturn(registry);
        when(registry.symbolsFor(any(MarketCategory.class))).thenReturn(List.of());
        when(registry.size()).thenReturn(3);

        mockMvc.perform(get("/api/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void symbolMaster_summary_whenNoParams() throws Exception {
        when(symbolMasterCache.size()).thenReturn(42);
        when(symbolMasterCache.allUnderlyings()).thenReturn(Set.of("NIFTY", "SBIN"));

        mockMvc.perform(get("/api/symbol-master"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSymbols").value(42));
    }

    @Test
    void symbolMaster_byTicker_found() throws Exception {
        SymbolMasterEntry entry = mock(SymbolMasterEntry.class);
        when(symbolMasterCache.byTicker("NSE:SBIN-EQ")).thenReturn(Optional.of(entry));
        mockMvc.perform(get("/api/symbol-master").param("ticker", "NSE:SBIN-EQ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists());
    }

    @Test
    void symbolMaster_byTicker_notFound() throws Exception {
        when(symbolMasterCache.byTicker("NSE:UNKNOWN-EQ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/symbol-master").param("ticker", "NSE:UNKNOWN-EQ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void parseSymbol_invalidFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/symbol/parse").param("s", "GARBAGE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dimensions_returnsAllTables() throws Exception {
        when(dimensionCache.allTables()).thenReturn(Map.of("exchanges", List.of()));

        mockMvc.perform(get("/api/dimensions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchanges").isArray());
    }

    @Test
    void dimensionTable_notFound_returns404() throws Exception {
        when(dimensionCache.tableByName("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/dimensions/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
