package com.cobingdaily.marketmaker.strategy;

import java.math.BigDecimal;
import java.time.Duration;

public record StrategyConfig(
        BigDecimal baseSpreadWidth,
        BigDecimal maxPosition,
        BigDecimal orderSize,
        BigDecimal maxCapital,
        Duration orderRefreshInterval,
        BigDecimal inventorySkewFactor,
        BigDecimal maxSpreadWidth,
        BigDecimal minSpreadWidth,
        String symbol,
        String traderId
) {}

