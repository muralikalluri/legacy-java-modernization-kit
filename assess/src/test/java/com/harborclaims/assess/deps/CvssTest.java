package com.harborclaims.assess.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CvssTest {

    @Test
    void knownBaseScores() {
        assertEquals(9.8, Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"));
        assertEquals(8.8, Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:H"));
        assertEquals(8.1, Cvss.baseScore("CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:H"));
        assertEquals(7.5, Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:H"));
        assertEquals(3.3, Cvss.baseScore("CVSS:3.1/AV:L/AC:L/PR:L/UI:N/S:U/C:L/I:N/A:N"));
        assertEquals(9.8, Cvss.baseScore("CVSS:3.0/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"));
    }

    @Test
    void scopeChangedVectorsUseTheChangedFormulas() {
        assertEquals(10.0, Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H"));
        assertEquals(9.9, Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:C/C:H/I:H/A:H"));
        assertEquals(6.1, Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N"));
    }

    @Test
    void noImpactIsZero() {
        assertEquals(0.0, Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:N"));
    }

    @Test
    void v4v2AndMalformedVectorsGiveNoScore() {
        assertNull(Cvss.baseScore("CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N"));
        assertNull(Cvss.baseScore("AV:N/AC:L/Au:N/C:P/I:P/A:P"));
        assertNull(Cvss.baseScore("CVSS:3.1/AV:X/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"));
        assertNull(Cvss.baseScore("CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H"));
        assertNull(Cvss.baseScore(null));
    }

    @Test
    void labelsFollowTheCvssBands() {
        assertEquals("NONE", Cvss.label(0.0));
        assertEquals("LOW", Cvss.label(3.9));
        assertEquals("MEDIUM", Cvss.label(4.0));
        assertEquals("MEDIUM", Cvss.label(6.9));
        assertEquals("HIGH", Cvss.label(7.0));
        assertEquals("HIGH", Cvss.label(8.9));
        assertEquals("CRITICAL", Cvss.label(9.0));
    }

    @Test
    void roundUpIsNotFooledByFloatingPointNoise() {
        assertEquals(4.0, Cvss.roundUp(4.0));
        assertEquals(4.0, Cvss.roundUp(3.9999999999));
        assertEquals(4.0, Cvss.roundUp(4.0000001), "the spec rounds to 5 decimals first, so this is exactly 4.0");
        assertEquals(4.1, Cvss.roundUp(4.00002));
    }
}
