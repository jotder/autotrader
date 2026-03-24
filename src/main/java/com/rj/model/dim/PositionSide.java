package com.rj.model.dim;

/**
 * Position side lookup — 1=Long, -1=Short, 0=Closed.
 * Source: {@code data/dim/position_sides.csv}
 */
public record PositionSide(int code, String name) {}
