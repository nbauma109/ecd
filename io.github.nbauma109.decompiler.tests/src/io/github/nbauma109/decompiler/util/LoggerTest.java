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
}
