package com.rj.engine;

import fyers.FyersOrderPlacement;
import com.rj.model.OpenPosition;
import com.rj.model.OrderFill;
import com.rj.model.OrderResult;
import com.rj.model.Signal;
import com.rj.model.TradeSignal;
import com.tts.in.model.FyersClass;
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
 *   <li>{@link #setAccessToken} must be called with a valid token before first use.</li>
 *   <li>All items in {@code docs/GO_LIVE_CHECKLIST.md} must be cleared.</li>
 * </ol>
 *
 * <h3>Retry policy</h3>
 * Up to 3 attempts with exponential backoff on null/error responses.
 * The same {@code clientOrderId} is carried on retries — the broker
 * deduplicates them, preventing double fills.
 *
 * <h3>Side convention (Fyers)</h3>
 * {@code 1} = BUY, {@code -1} = SELL.
 */
public class LiveOrderExecutor implements IOrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(LiveOrderExecutor.class);

    private static final int  MAX_RETRIES     = 3;
    private static final long BASE_BACKOFF_MS = 500L;

    /** Fyers product type for intraday trading. */
    private static final String PRODUCT_INTRADAY = "INTRADAY";

    private final FyersOrderPlacement fyersOrders;
    private final AtomicInteger       orderSeq = new AtomicInteger(0);

    public LiveOrderExecutor() {
        this.fyersOrders = new FyersOrderPlacement();
    }

    /** Updates the access token on FyersClass so all subsequent API calls use it. */
    public void setAccessToken(String token) {
        FyersClass.getInstance().accessToken = token;
        log.info("LiveOrderExecutor: access token updated");
    }

    // ── IOrderExecutor ────────────────────────────────────────────────────────

    @Override
    public OrderFill placeEntry(TradeSignal signal, int quantity) {
        int  side         = signal.getDirection() == Signal.BUY ? 1 : -1;
        PlaceOrderModel model = FyersOrderPlacement.marketOrder(
                signal.getSymbol(), quantity, side, PRODUCT_INTRADAY);

        log.info("[LIVE] Placing entry: {} {} qty={} correlationId={}",
                signal.getSymbol(), signal.getDirection(), quantity,
                signal.getCorrelationId());

        return executeWithRetry(model, "ENTRY:" + signal.getCorrelationId());
    }

    @Override
    public OrderFill placeExit(OpenPosition position,
                               PositionMonitor.ExitReason reason,
                               double exitPrice) {
        // Exit direction is opposite to entry direction
        int side = position.getDirection() == Signal.BUY ? -1 : 1;

        PlaceOrderModel model = FyersOrderPlacement.marketOrder(
                position.getSymbol(), position.getQuantity(), side, PRODUCT_INTRADAY);

        log.info("[LIVE] Placing exit: {} {} qty={} reason={} triggerPrice={}",
                position.getSymbol(), position.getDirection(),
                position.getQuantity(), reason,
                String.format("%.2f", exitPrice));

        return executeWithRetry(model, "EXIT:" + position.getCorrelationId());
    }

    // ── Retry loop ────────────────────────────────────────────────────────────

    /**
     * Attempts to place an order up to {@link #MAX_RETRIES} times.
     * Returns on first success. Returns a rejected fill if all attempts fail.
     */
    private OrderFill executeWithRetry(PlaceOrderModel model, String tag) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                OrderResult result = fyersOrders.placeOrder(model);

                if (result == null) {
                    log.warn("[LIVE][{}] Attempt {}/{}: null response from Fyers API",
                            tag, attempt, MAX_RETRIES);
                    backoff(attempt);
                    continue;
                }

                if (result.isOk()) {
                    log.info("[LIVE][{}] Order placed: broker_id={}", tag, result.id);
                    return OrderFill.success(result.id, model.LimitPrice > 0 ? model.LimitPrice : 0,
                            model.Qty, Instant.now());
                }

                // Non-OK response: log and decide whether to retry
                log.warn("[LIVE][{}] Attempt {}/{}: broker rejected — code={} message={}",
                        tag, attempt, MAX_RETRIES, result.code, result.message);

                // HTTP 4xx codes (except 429): do not retry
                if (result.code >= 400 && result.code < 500 && result.code != 429) {
                    return OrderFill.rejected("Broker rejected order: " + result.message
                            + " (code=" + result.code + ")");
                }

                backoff(attempt);

            } catch (Exception e) {
                log.error("[LIVE][{}] Attempt {}/{}: exception — {}",
                        tag, attempt, MAX_RETRIES, e.getMessage(), e);
                backoff(attempt);
            }
        }

        String msg = "All " + MAX_RETRIES + " attempts failed for order " + tag;
        log.error("[LIVE] {}", msg);
        return OrderFill.rejected(msg);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void backoff(int attempt) {
        long delayMs = BASE_BACKOFF_MS * (1L << (attempt - 1)); // 500, 1000, 2000 ms
        log.warn("[LIVE] Backing off {}ms before retry", delayMs);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
