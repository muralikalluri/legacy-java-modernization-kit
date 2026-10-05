package com.harborclaims.modern.common;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Explicit formats, never the JVM default locale. */
public final class Fmt {

    private Fmt() {
    }

    public static String money(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(value);
    }
}
