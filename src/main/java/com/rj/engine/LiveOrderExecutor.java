package com.rj.engine;

import com.rj.broker.IOrderAdapter;
import com.rj.engine.ExitReason;
import com.rj.fyers.FyersBrokerAdapter;
import com.rj.model.*;
import com.tts.in.model.PlaceOrderModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Live order executor — places real orders via Fyers API.
 *
 * <h3>Pre-conditions</h3>
 * <ol>
 *   <li>{@code APP_ENV=live} must be set in {@code .env}.</li>
 *   <li>All items in {@code docs/GO_LIVE_CHECKLIST.md} must be cleared.</li>
 * </ol>
 *
 * <h3>Retry policy</h3>
 * Delegated to {@link BrokerCircuitBreaker}: up to 3 attempts with exponential
 * backoff + jitter. Never retry 4xx except 429. Circuit breaker fast-fails
 * when broker is unavailable.
 *
 * <h3>Side convention (Fyers)</h3>
 * {@code 1} = BUY, {@code -1} = SELL.
 */
public class LiveOrderExecutor implements IOrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(LiveOrderExecutor.class);

    /** Default product type when instrument info is not available. */
    private static final String DEFAULT_PRODUCT_TYPE = "INTRADAY";

    private final IOrderAdapter orderAdapter;
    private volatile BrokerCircuitBreaker circuitBreaker;
    private final AtomicInteger orderSeq = new AtomicInteger(0);

    public LiveOrderExecutor(IOrderAdapter orderAdapter) {
        this(orderAdapter, null);
    }

    public LiveOrderExecutor(IOrderAdapter orderAdapter, BrokerCircuitBreaker circuitBreaker) {
        this.orderAdapter = orderAdapter;
        this.circuitBreaker = circuitBreaker;
    }

    /** Attach circuit breaker after construction (used when wiring at engine startup). */
    public void setCircuitBreaker(BrokerCircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    // ── IOrderExecutor ────────────────────────────────────────────────────────

    @Override
    public OrderFill placeEntry(TradeSignal signal, int quantity) {
        int side = signal.getDirection() == Signal.BUY ? 1 : -1;

        // F&O Support: Derivatives MUST use MARGIN product type
        String productType = signal.getProductType();
        if (signal.getInstrumentInfo() != null && signal.getInstrumentInfo().isDerivative()) {
            productType = "MARGIN";
        }

        PlaceOrderModel model = FyersBrokerAdapter.marketOrder(
                signal.getSymbol(), quantity, side, productType);

        log.info("[LIVE] Placing entry: {} {} qty={} product={} correlationId={}",
                signal.getSymbol(), signal.getDirection(), quantity, productType,
                signal.getCorrelationId());

        return executeViaCircuitBreaker(model, "ENTRY:" + signal.getCorrelationId(), true);
    }

    @Override
    public OrderFill placeExit(OpenPosition position,
                               ExitReason reason,
                               double exitPrice) {
        // Exit direction is opposite to entry direction
        int side = position.getDirection() == Signal.BUY ? -1 : 1;

        String productType = position.getProductType();
        if (position.getInstrumentInfo() != null && position.getInstrumentInfo().isDerivative()) {
            productType = "MARGIN";
        }

        PlaceOrderModel model = FyersBrokerAdapter.marketOrder(
                position.getSymbol(), position.getQuantity(), side, productType);

        log.info("[LIVE] Placing exit: {} {} qty={} product={} reason={} triggerPrice={}",
                position.getSymbol(), position.getDirection(),
                position.getQuantity(), productType, reason,
                String.format("%.2f", exitPrice));

        // Exits are always critical — capital protection
        return executeViaCircuitBreaker(model, "EXIT:" + position.getCorrelationId(), true);
    }

    // ── Circuit-breaker-wrapped execution ────────────────────────────────────

    private OrderFill executeViaCircuitBreaker(PlaceOrderModel model, String tag, boolean isCritical) {
        if (circuitBreaker == null) {
            // Fallback: direct call without circuit breaker (e.g., before wiring)
            return directCall(model, tag);
        }
        try {
            return circuitBreaker.execute(() -> {
                OrderResult result = orderAdapter.placeOrder(model);

                if (result == null) {
                    throw new BrokerCircuitBreaker.BrokerApiException(
                            "Null response from Fyers API for " + tag);
                }

                if (result.isOk()) {
                    log.info("[LIVE][{}] Order placed: broker_id={}", tag, result.id);
                    return OrderFill.success(result.id, model.LimitPrice > 0 ? model.LimitPrice : 0,
                            model.Qty, Instant.now());
                }

                // Non-OK: throw typed exception so circuit breaker can classify
                if (result.code >= 400 && result.code < 500) {
                    throw new BrokerCircuitBreaker.BrokerHttpException(result.code,
                            "Broker rejected: " + result.message + " (code=" + result.code + ")");
                }

                // Server error → generic exception → retryable
                throw new BrokerCircuitBreaker.BrokerApiException(
                        "Broker error: " + result.message + " (code=" + result.code + ")");

            }, isCritical);

        } catch (BrokerCircuitBreaker.CircuitBreakerOpenException e) {
            log.error("[LIVE][{}] Circuit breaker OPEN — order rejected", tag);
            return OrderFill.rejected("Circuit breaker OPEN — broker unavailable");

        } catch (BrokerCircuitBreaker.BrokerHttpException e) {
            log.warn("[LIVE][{}] Broker HTTP error: {}", tag, e.getMessage());
            return OrderFill.rejected(e.getMessage());

        } catch (BrokerCircuitBreaker.BrokerApiException e) {
            log.error("[LIVE][{}] All retries failed: {}", tag, e.getMessage());
            return OrderFill.rejected(e.getMessage());
        }
    }

    /** Direct broker call without circuit breaker (fallback). */
    private OrderFill directCall(PlaceOrderModel model, String tag) {
        try {
            OrderResult result = orderAdapter.placeOrder(model);
            if (result == null) {
                return OrderFill.rejected("Null response from Fyers API");
            }
            if (result.isOk()) {
                return OrderFill.success(result.id, model.LimitPrice > 0 ? model.LimitPrice : 0,
                        model.Qty, Instant.now());
            }
            return OrderFill.rejected("Broker rejected: " + result.message);
        } catch (Exception e) {
            log.error("[LIVE][{}] Direct call failed: {}", tag, e.getMessage(), e);
            return OrderFill.rejected("Direct call failed: " + e.getMessage());
        }
    }
}
