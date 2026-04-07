package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.engine.HealthMonitor;
import com.rj.engine.PositionBook;
import com.rj.engine.ScheduledPositionManager;
import com.rj.engine.TradingEngine;
import com.rj.engine.TradeJournal;
import com.rj.model.ExecutionMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingEngine engine;

    @MockBean
    private ConfigManager configManager;

    @Test
    void status_returnsEngineState() throws Exception {
        when(engine.isRunning()).thenReturn(true);
        when(engine.getMode()).thenReturn(ExecutionMode.PAPER);
        when(configManager.getActiveSymbols()).thenReturn(new String[]{"NSE:NIFTY50-INDEX"});

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.mode").value("PAPER"))
                .andExpect(jsonPath("$.activeSymbols[0]").value("NSE:NIFTY50-INDEX"));
    }

    @Test
    void health_returnsComponentStatus() throws Exception {
        ScheduledPositionManager spm = mock(ScheduledPositionManager.class);
        HealthMonitor hm = mock(HealthMonitor.class);
        TradeJournal journal = mock(TradeJournal.class);
        PositionBook positionBook = mock(PositionBook.class);

        when(engine.isRunning()).thenReturn(true);
        when(engine.getScheduledPositionManager()).thenReturn(spm);
        when(engine.getHealthMonitor()).thenReturn(hm);
        when(engine.getJournal()).thenReturn(journal);
        when(engine.getPositionBook()).thenReturn(positionBook);
        when(spm.isRunning()).thenReturn(true);
        when(hm.isRunning()).thenReturn(true);
        when(positionBook.openPositionCount()).thenReturn(2);
        when(journal.closedTradeCount()).thenReturn(5);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineRunning").value(true))
                .andExpect(jsonPath("$.scheduledPositionManagerRunning").value(true))
                .andExpect(jsonPath("$.openPositionCount").value(2))
                .andExpect(jsonPath("$.closedTradeCount").value(5));
    }
}
