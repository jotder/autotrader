package com.rj.model;

import java.time.Instant;

/**
 * Result of an order execution attempt — returned by all {@code IOrderExecutor} implementations.
 * Immutable.
 */
public final class OrderFill {

    private final String        orderId;
    private final double        fillPrice;
    private final int           fillQuantity;
    private final Instant       fillTime;
    private final boolean       success;
    private final String        rejectReason;   // null when success

    private OrderFill(String orderId, double fillPrice, int fillQuantity,
                      Instant fillTime, boolean success, String rejectReason) {
        this.orderId      = orderId;
        this.fillPrice    = fillPrice;
        this.fillQuantity = fillQuantity;
        this.fillTime     = fillTime;
        this.success      = success;
        this.rejectReason = rejectReason;
    }

    public static OrderFill success(String orderId, double fillPrice,
                                    int fillQuantity, Instant fillTime) {
        return new OrderFill(orderId, fillPrice, fillQuantity, fillTime, true, null);
    }

    public static OrderFill rejected(String reason) {
        return new OrderFill(null, 0, 0, Instant.now(), false, reason);
    }

    public String  getOrderId()      { return orderId;      }
    public double  getFillPrice()    { return fillPrice;    }
    public int     getFillQuantity() { return fillQuantity; }
    public Instant getFillTime()     { return fillTime;     }
    public boolean isSuccess()       { return success;      }
    public String  getRejectReason() { return rejectReason; }

    @Override
    public String toString() {
        return success
                ? String.format("OrderFill{id=%s price=%.2f qty=%d}", orderId, fillPrice, fillQuantity)
                : String.format("OrderFill{REJECTED reason=%s}", rejectReason);
    }
}
