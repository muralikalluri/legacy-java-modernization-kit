package com.harborclaims.portal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class BuildInfoTest {

    @Test
    public void appNameIsStable() {
        assertEquals("Harbor Claims Portal", BuildInfo.APP_NAME);
    }

    @Test
    public void reportsJavaVersion() {
        assertNotNull(BuildInfo.javaSpecVersion());
    }
}
