package software.amazon.lambda.powertools.logging.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.INFO;
import static org.slf4j.event.Level.WARN;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

class LogbackLoggingManagerTest {

    private static Logger LOG = LoggerFactory.getLogger(LogbackLoggingManagerTest.class);
    private static Logger ROOT = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @Test
    @Order(1)
    void getLogLevel_shouldReturnConfiguredLogLevel() {
        LogbackLoggingManager manager = new LogbackLoggingManager();
        Level logLevel = manager.getLogLevel(LOG);
        assertThat(logLevel).isEqualTo(INFO);

        logLevel = manager.getLogLevel(ROOT);
        assertThat(logLevel).isEqualTo(WARN);
    }

    @Test
    @Order(2)
    void resetLogLevel() {
        LogbackLoggingManager manager = new LogbackLoggingManager();
        manager.resetLogLevel(ERROR);

        Level logLevel = manager.getLogLevel(LOG);
        assertThat(logLevel).isEqualTo(ERROR);
    }
}
