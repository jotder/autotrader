package com.rj.web;

import com.rj.config.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StrategyController.class)
class StrategyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StrategyService strategyService;

    @Test
    void testListStrategies() throws Exception {
        StrategyVersionInfo info = new StrategyVersionInfo(
                "test_strat", 1, 1, true, "ACTIVE", List.of(),
                new StrategyYamlConfig(), null);
        
        when(strategyService.getAllStrategies()).thenReturn(List.of(info));

        mockMvc.perform(get("/api/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].strategyId").value("test_strat"))
                .andExpect(jsonPath("$[0].activeVersion").value(1));
    }

    @Test
    void testGetStrategy() throws Exception {
        StrategyVersionInfo info = new StrategyVersionInfo(
                "test_strat", 1, 1, true, "ACTIVE", List.of(),
                new StrategyYamlConfig(), null);

        when(strategyService.getStrategy("test_strat")).thenReturn(info);

        mockMvc.perform(get("/api/strategies/test_strat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyId").value("test_strat"));
    }
}
