package com.harborclaims.modern.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PagingTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-3", "abc", "1.5", "99999999999", " "})
    void notAPositiveWholeNumberMeansPageOne(String raw) {
        assertEquals(1, Paging.parsePage(raw), raw);
    }

    @Test
    void nullMeansPageOne() {
        assertEquals(1, Paging.parsePage(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {" 2 ", "+2", "2"})
    void paddedAndSignedNumbersAreAccepted(String raw) {
        assertEquals(2, Paging.parsePage(raw), raw);
    }

    @Test
    void offsetsAndPageCounts() {
        assertEquals(0, Paging.offset(1));
        assertEquals(20, Paging.offset(3));
        assertEquals(Integer.MAX_VALUE, Paging.offset(Integer.MAX_VALUE));
        assertEquals(0, new Page<String>(List.of(), 1, 0).totalPages());
        assertEquals(1, new Page<String>(List.of(), 1, 10).totalPages());
        assertEquals(2, new Page<String>(List.of(), 1, 11).totalPages());
        assertEquals(true, new Page<String>(List.of(), 2, 30).hasPrev());
        assertEquals(false, new Page<String>(List.of(), 3, 30).hasNext());
    }

    @Test
    void moneyIsFormattedWithTheUsLocale() {
        assertEquals("50,000.00", Fmt.money(new BigDecimal("50000")));
        assertEquals("1,234.50", Fmt.money(new BigDecimal("1234.5")));
        assertEquals("", Fmt.money(null));
    }
}
