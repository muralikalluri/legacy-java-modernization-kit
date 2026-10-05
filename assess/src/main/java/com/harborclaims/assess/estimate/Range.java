package com.harborclaims.assess.estimate;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** A [low, high] band of person-days. Bands add by summing lows and highs separately. */
public record Range(BigDecimal low, BigDecimal high) {

    public static final Range ZERO = new Range(BigDecimal.ZERO, BigDecimal.ZERO);

    public Range add(Range o) {
        return new Range(low.add(o.low), high.add(o.high));
    }

    public Range times(BigDecimal factor) {
        return new Range(low.multiply(factor), high.multiply(factor));
    }

    /** Low rounded down and high rounded up to a multiple of step, as one decimal. */
    public Range roundedOutward(BigDecimal step) {
        BigDecimal l = low.divide(step, 0, RoundingMode.FLOOR).multiply(step);
        BigDecimal h = high.divide(step, 0, RoundingMode.CEILING).multiply(step);
        return new Range(l.setScale(1, RoundingMode.UNNECESSARY), h.setScale(1, RoundingMode.UNNECESSARY));
    }

    public BigDecimal midpoint() {
        return low.add(high).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }
}
