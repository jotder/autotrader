package com.rj.engine.disruptor;

import com.lmax.disruptor.EventHandler;
import com.rj.model.Tick;
import com.rj.model.TickStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer that updates the central TickStore with new data.
 */
public class TickStoreUpdater implements EventHandler<TickEvent> {
    private static final Logger log = LoggerFactory.getLogger(TickStoreUpdater.class);
    private final TickStore tickStore = TickStore.getInstance();

    @Override
    public void onEvent(TickEvent event, long sequence, boolean endOfBatch) {
        Tick tick = event.getTick();
        if (tick != null) {
            tickStore.append(tick);
        }
    }
}
