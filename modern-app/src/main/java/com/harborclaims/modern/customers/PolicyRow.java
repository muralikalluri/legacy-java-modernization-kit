package com.harborclaims.modern.customers;

import com.harborclaims.modern.common.Fmt;
import java.math.BigDecimal;

public record PolicyRow(String policyNumber, String productType, BigDecimal coverageLimit, String startDate, String endDate, String status) {

    public String coverageLimitDisplay() {
        return Fmt.money(coverageLimit);
    }
}
