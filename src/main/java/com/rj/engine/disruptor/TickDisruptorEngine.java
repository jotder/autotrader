package com.rj.engine.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.rj.model.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

/**
 * Orchestrator for the LMAX Disruptor tick pipeline.
 * High-performance, lock-free handoff from WebSocket thread to analytical consumers.
 */
@Component
public class TickDisruptorEngine {
    private static final Logger log = LoggerFactory.getLogger(TickDisruptorEngine.class);
    private static final int RING_BUFFER_SIZE = 16384; // Must be power of 2

    private Disruptor<TickEvent> disruptor;
    private RingBuffer<TickEvent> ringBuffer;
    private final List<EventHandler<TickEvent>> handlers = new ArrayList<>();

    public void addHandler(EventHandler<TickEvent> handler) {
        if (disruptor != null) {
            throw new IllegalStateException("Cannot add handler after Disruptor has started");
        }
        this.handlers.add(handler);
    }

    /**
     * Initializes the Disruptor with mandated Virtual Threads for execution.
     */
    public void start() {
        if (disruptor != null) {
            log.warn("TickDisruptorEngine already started");
            return;
        }

        log.info("Starting TickDisruptorEngine (size={}, handlers={})...", RING_BUFFER_SIZE, handlers.size());

        ThreadFactory threadFactory = Thread.ofVirtual().name("disruptor-worker-", 0).factory();

        disruptor = new Disruptor<>(
                new TickEventFactory(),
                RING_BUFFER_SIZE,
                threadFactory,
                ProducerType.SINGLE,
                new BlockingWaitStrategy()
        );

        if (handlers.isEmpty()) {
            log.warn("No handlers registered for TickDisruptorEngine — ticks will be dropped!");
        } else {
            disruptor.handleEventsWith(handlers.toArray(new EventHandler[0]));
        }

        ringBuffer = disruptor.start();
        log.info("TickDisruptorEngine started.");
    }

    /**
     * Publishes a tick to the ring buffer.
     * This is the "Hot Path" entry point.
     */
    public void publish(Tick tick) {
        if (ringBuffer == null) return;

        long sequence = ringBuffer.next();
        try {
            TickEvent event = ringBuffer.get(sequence);
            event.setTick(tick);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    /**
     * Graceful shutdown of the pipeline.
     */
    public void stop() {
        if (disruptor == null) return;
        log.info("Stopping TickDisruptorEngine...");
        disruptor.shutdown();
        disruptor = null;
        ringBuffer = null;
        log.info("TickDisruptorEngine stopped.");
    }
}
