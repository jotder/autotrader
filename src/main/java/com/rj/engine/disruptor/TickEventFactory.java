package com.rj.engine.disruptor;

import com.lmax.disruptor.EventFactory;

/**
 * Pre-allocates TickEvent objects in the RingBuffer to avoid GC pressure.
 */
public class TickEventFactory implements EventFactory<TickEvent> {
    @Override
    public TickEvent newInstance() {
        return new TickEvent();
    }
}
