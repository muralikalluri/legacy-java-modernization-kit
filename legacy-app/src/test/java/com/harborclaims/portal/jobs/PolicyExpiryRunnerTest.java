package com.harborclaims.portal.jobs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.concurrent.Callable;

import org.junit.Test;

public class PolicyExpiryRunnerTest {

    @Test
    public void aFreeLockRunsTheBody() throws Exception {
        assertEquals("ran", PolicyExpiryRunner.guarded(new Callable<String>() {
            public String call() {
                return "ran";
            }
        }));
    }

    @Test
    public void aSecondRunWhileOneIsActiveIsRefused() throws Exception {
        String outer = PolicyExpiryRunner.guarded(new Callable<String>() {
            public String call() throws Exception {
                String inner = PolicyExpiryRunner.guarded(new Callable<String>() {
                    public String call() {
                        return "inner ran";
                    }
                });
                assertNull("overlapping run must be refused", inner);
                return "outer ran";
            }
        });
        assertEquals("outer ran", outer);
    }

    @Test
    public void theLockIsReleasedAfterAFailure() throws Exception {
        try {
            PolicyExpiryRunner.guarded(new Callable<String>() {
                public String call() {
                    throw new IllegalStateException("boom");
                }
            });
        } catch (IllegalStateException expected) {
            // fine
        }
        assertEquals("again", PolicyExpiryRunner.guarded(new Callable<String>() {
            public String call() {
                return "again";
            }
        }));
    }
}
