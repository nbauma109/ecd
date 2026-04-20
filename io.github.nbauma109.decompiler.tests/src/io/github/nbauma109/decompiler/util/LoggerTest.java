package io.github.nbauma109.decompiler.util;

import org.junit.Test;

public class LoggerTest {

    @Test
    public void debugAndGuardClausesHandleNullInputs() {
        Logger.debug((String) null, null);
        Logger.debug(new RuntimeException("boom"));
        Logger.info(null);
        Logger.warn(null);
        Logger.error((String) null);
        Logger.error((String) null, new RuntimeException("ignored"));
        Logger.error((Throwable) null);
    }

    @Test
    public void debugPrintsMessageAndThrowableWithoutThrowing() {
        Logger.debug("debug message", new RuntimeException("boom"));
    }

    @Test
    public void infoWithNonNullMessageDoesNotThrow() {
        Logger.info("info message");
    }

    @Test
    public void warnWithNonNullMessageDoesNotThrow() {
        Logger.warn("warn message");
    }

    @Test
    public void errorWithNonNullMessageDoesNotThrow() {
        Logger.error("error message");
    }

    @Test
    public void errorWithNonNullMessageAndThrowableDoesNotThrow() {
        Logger.error("error with cause", new RuntimeException("cause"));
    }

    @Test
    public void errorWithNonNullThrowableDoesNotThrow() {
        Logger.error(new RuntimeException("throwable only"));
    }
}
