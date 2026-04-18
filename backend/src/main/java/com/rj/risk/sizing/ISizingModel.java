package com.rj.risk.sizing;

import com.rj.model.OpenPosition;
import com.rj.model.TradeSignal;

import java.util.Collection;

/**
 * Interface for pluggable position sizing models in Phase-II.
 */
public interface ISizingModel {

    /**
     * Calculates the quantity to trade based on the sizing model.
     * @param signal The generated trade signal.
     * @param currentCapital Available capital for THIS strategy bucket.
     * @return The computed quantity (raw, before lot-alignment).
     */
    double calculateQuantity(TradeSignal signal, double currentCapital);

    /**
     * Descriptive name of the model.
     */
    String getName();
}
