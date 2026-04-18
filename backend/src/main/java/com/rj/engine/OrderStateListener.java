package com.rj.engine;

/** Callback for OMS order state changes. */
@FunctionalInterface
public interface OrderStateListener {
    void onStateChange(ManagedOrder order, ManagedOrder.StateTransition transition);
}
