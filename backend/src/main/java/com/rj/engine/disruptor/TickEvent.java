package com.rj.engine.disruptor;

import com.rj.model.Tick;

/**
 * Mutable event wrapper for the LMAX Disruptor ring buffer.
 */
public class TickEvent {
    private Tick tick;

    public Tick getTick() {
        return tick;
    }

    public void setTick(Tick tick) {
        this.tick = tick;
    }

    @Override
    public String toString() {
        return "TickEvent{tick=" + tick + "}";
    }
}
