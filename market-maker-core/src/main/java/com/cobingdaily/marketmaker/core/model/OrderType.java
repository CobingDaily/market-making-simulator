package com.cobingdaily.marketmaker.core.model;

/**
 * Represents 2 types of orders in the market
 * <p>
 * MARKET order:
 * - Executes immediately at the best available price
 * - Prioritizes speed over price
 * <p>
 * LIMIT order:
 * - Only executes at a specified price or better
 * - Prioritizes price over speed
 */
public enum OrderType {
    MARKET,
    LIMIT
}

