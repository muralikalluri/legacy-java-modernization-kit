package com.harborclaims.portal.common;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;

public class PagingTest {

    @Test
    public void badPageNumbersMeanPageOne() {
        assertEquals(1, Paging.parsePage(null));
        assertEquals(1, Paging.parsePage(""));
        assertEquals(1, Paging.parsePage("abc"));
        assertEquals(1, Paging.parsePage("0"));
        assertEquals(1, Paging.parsePage("-3"));
        assertEquals(1, Paging.parsePage("99999999999"));
    }

    @Test
    public void validPageNumbersAreKept() {
        assertEquals(3, Paging.parsePage(" 3 "));
        assertEquals(20, Paging.offset(3));
        assertEquals(0, Paging.offset(1));
    }

    @Test
    public void totalPagesRoundsUp() {
        assertEquals(0, new Page<String>(Collections.<String>emptyList(), 1, 0).getTotalPages());
        assertEquals(1, new Page<String>(Collections.<String>emptyList(), 1, 10).getTotalPages());
        assertEquals(2, new Page<String>(Collections.<String>emptyList(), 1, 11).getTotalPages());
    }

    @Test
    public void moneyFormatIgnoresDefaultLocale() {
        assertEquals("1,234.50", Fmt.money(new java.math.BigDecimal("1234.5")));
        assertEquals("", Fmt.money(null));
    }
}
