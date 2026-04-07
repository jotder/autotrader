package com.rj.web;

import com.rj.broker.ITickFeed;
import com.rj.engine.TradingEngine;
import com.rj.model.TickStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EngineController.class)
class EngineControllerConnectTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingEngine engine;

    @MockBean
    private TickStore tickStore;

    @MockBean
    private ITickFeed brokerFeed;

    @Test
    void connect_withToken_returns200AndCallsConnect() throws Exception {
        mockMvc.perform(post("/api/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"test-token-abc\"}"))
                .andExpect(status().isOk());

        verify(brokerFeed).connect("test-token-abc");
    }

    @Test
    void connect_withBlankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
