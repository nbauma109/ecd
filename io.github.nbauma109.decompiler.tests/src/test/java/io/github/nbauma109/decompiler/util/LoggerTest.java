/*******************************************************************************
 * (C) 2026 Claude (@Claude)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package io.github.nbauma109.decompiler.util;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class LoggerTest {

    private static void assertNoException(Runnable action) {
        boolean threw = false;
        try {
            action.run();
        } catch (Exception e) {
            threw = true;
        }
        assertFalse("Expected no exception to be thrown", threw);
    }

    @Test
    public void debugAndGuardClausesHandleNullInputs() {
        assertNoException(() -> {
            Logger.debug((String) null, null);
            Logger.debug(new RuntimeException("boom"));
            Logger.info(null);
            Logger.warn(null);
            Logger.error((String) null);
            Logger.error((String) null, new RuntimeException("ignored"));
            Logger.error((Throwable) null);
        });
    }

    @Test
    public void debugPrintsMessageAndThrowableWithoutThrowing() {
        assertNoException(() -> Logger.debug("debug message", new RuntimeException("boom")));
    }

    @Test
    public void infoWithNonNullMessageDoesNotThrow() {
        assertNoException(() -> Logger.info("info message"));
    }

    @Test
    public void warnWithNonNullMessageDoesNotThrow() {
        assertNoException(() -> Logger.warn("warn message"));
    }

    @Test
    public void errorWithNonNullMessageDoesNotThrow() {
        assertNoException(() -> Logger.error("error message"));
    }

    @Test
    public void errorWithNonNullMessageAndThrowableDoesNotThrow() {
        assertNoException(() -> Logger.error("error with cause", new RuntimeException("cause")));
    }

    @Test
    public void errorWithNonNullThrowableDoesNotThrow() {
        assertNoException(() -> Logger.error(new RuntimeException("throwable only")));
    }
}
