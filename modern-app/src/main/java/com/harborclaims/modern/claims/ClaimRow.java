package com.harborclaims.modern.claims;

import com.harborclaims.modern.common.Fmt;
import java.math.BigDecimal;

public record ClaimRow(int id, String claimNumber, String customerName, String policyNumber, String status, String lossDate,
        BigDecimal amountClaimed) {

    public String amountClaimedDisplay() {
        return Fmt.money(amountClaimed);
    }
}
