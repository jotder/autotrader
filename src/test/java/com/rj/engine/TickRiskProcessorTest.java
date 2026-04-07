package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.engine.disruptor.TickEvent;
import com.rj.engine.risk.RiskSessionState;
import com.rj.model.OpenPosition;
import com.rj.model.Signal;
import com.rj.model.Tick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TickRiskProcessorTest {

    @Mock private RiskSessionState riskState;

    private PositionBook positionBook;
    private RiskConfig riskConfig;
    private TickRiskProcessor processor;

    private final AtomicReference<ExitReason> capturedReason = new AtomicReference<>();
    private BiConsumer<OpenPosition, ExitReason> exitHandler;

    @BeforeEach
    void setUp() {
        riskConfig   = RiskConfig.defaults();
        positionBook = new PositionBook();
        processor    = new TickRiskProcessor(positionBook, riskState, riskConfig);
        exitHandler  = (pos, reason) -> capturedReason.set(reason);
        processor.setExitHandler(exitHandler);
    }

    @Test
    void slHit_callsExitHandlerWithStopLoss() {
        OpenPosition pos = new OpenPosition(
                "NSE:SBIN-EQ", "corr-1", "strat", Signal.BUY,
                100.0, 10, 95.0, 110.0, Instant.now());
        positionBook.add(pos);

        when(riskState.isKillSwitchActive()).thenReturn(false);
        fireEvent("NSE:SBIN-EQ", 94.0); // below SL of 95

        assertEquals(ExitReason.STOP_LOSS, capturedReason.get());
        assertTrue(positionBook.isEmpty(), "Position must be removed after exit");
    }

    @Test
    void tpHit_callsExitHandlerWithTakeProfit() {
        OpenPosition pos = new OpenPosition(
                "NSE:SBIN-EQ", "corr-2", "strat", Signal.BUY,
                100.0, 10, 95.0, 110.0, Instant.now());
        positionBook.add(pos);

        when(riskState.isKillSwitchActive()).thenReturn(false);
        fireEvent("NSE:SBIN-EQ", 111.0); // above TP of 110

        assertEquals(ExitReason.TAKE_PROFIT, capturedReason.get());
    }

    @Test
    void tickForUnwatchedSymbol_isNoOp() {
        OpenPosition pos = new OpenPosition(
                "NSE:SBIN-EQ", "corr-3", "strat", Signal.BUY,
                100.0, 10, 95.0, 110.0, Instant.now());
        positionBook.add(pos);

        when(riskState.isKillSwitchActive()).thenReturn(false);
        fireEvent("NSE:RELIANCE-EQ", 50.0); // different symbol

        assertNull(capturedReason.get(), "No exit for different symbol");
    }

    @Test
    void killSwitchActiveAndNotAnomalyMode_returnsEarly() {
        OpenPosition pos = new OpenPosition(
                "NSE:SBIN-EQ", "corr-4", "strat", Signal.BUY,
                100.0, 10, 95.0, 110.0, Instant.now());
        positionBook.add(pos);

        when(riskState.isKillSwitchActive()).thenReturn(true);
        when(riskState.isAnomalyMode()).thenReturn(false);
        fireEvent("NSE:SBIN-EQ", 94.0); // would be SL hit

        assertNull(capturedReason.get(), "No exit when kill switch is active without anomaly mode");
    }

    // ── Helper — Tick uses a Builder, not a simple (symbol, price) constructor ─
    private void fireEvent(String symbol, double price) {
        Tick tick = new Tick.Builder()
                .symbol(symbol)
                .ltp(price)
                .build();
        TickEvent event = new TickEvent();
        event.setTick(tick);
        processor.onEvent(event, 0, true);
    }
}
