package com.harborclaims.portal.common;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Explicit formats so output never depends on the JVM default locale. */
public final class Fmt {

    private Fmt() {
    }

    public static String money(BigDecimal value) {
        if (value == null) {
            return "";
        }
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return df.format(value);
    }

    public static String blank(String value) {
        return value == null ? "" : value;
    }
}
