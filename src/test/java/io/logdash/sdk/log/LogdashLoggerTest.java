package io.logdash.sdk.log;

import io.logdash.sdk.config.LogdashConfig;
import io.logdash.sdk.transport.LogdashTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class LogdashLoggerTest {

    @Mock
    private LogdashTransport transport;

    private LogdashConfig config;
    private LogdashLogger logger;

    @BeforeEach
    void setUp() {
        config =
                new LogdashConfig(
                        "test-key", "https://api.logdash.io", false, false, 3, 1000L, 5000L, 10000L, 100);

        given(transport.sendLog(any())).willReturn(CompletableFuture.completedFuture(null));
        logger = new LogdashLogger(config, transport);
    }

    @Test
    void should_send_info_log_with_correct_level() {
        // Act
        logger.info("test message");

        // Assert
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        then(transport).should().sendLog(captor.capture());

        LogEntry captured = captor.getValue();
        assertThat(captured.level()).isEqualTo(LogLevel.INFO);
        assertThat(captured.message()).isEqualTo("test message");
    }

    @Test
    void should_send_log_with_context() {
        // Arrange
        final Map<String, Object> context = Map.of("userId", "123", "action", "login");

        // Act
        logger.info("User logged in", context);

        // Assert
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        then(transport).should().sendLog(captor.capture());

        LogEntry captured = captor.getValue();
        assertThat(captured.message()).contains("User logged in");
        assertThat(captured.message()).contains("userId");
        assertThat(captured.message()).contains("123");
    }

    @Test
    void should_handle_all_log_levels() {
        // Act
        logger.error("error message");
        logger.warn("warn message");
        logger.info("info message");
        logger.http("http message");
        logger.verbose("verbose message");
        logger.debug("debug message");
        logger.silly("silly message");

        // Assert
        then(transport).should(times(7)).sendLog(any(LogEntry.class));
    }

    @Test
    void should_handle_large_context_maps() {
        // Arrange
        Map<String, Object> largeContext = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            largeContext.put("key" + i, "value" + i);
        }

        // Act & Assert
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            logger.info("Large context test", largeContext);
                            then(transport).should().sendLog(any(LogEntry.class));
                        });
    }

    @Test
    void should_handle_null_values_in_context() {
        // Arrange
        Map<String, Object> contextWithNull = new HashMap<>();
        contextWithNull.put("validKey", "validValue");
        contextWithNull.put("nullKey", null);

        // Act & Assert
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            logger.info("Null context test", contextWithNull);
                            then(transport).should().sendLog(any(LogEntry.class));
                        });
    }

    @Test
    void should_increment_sequence_numbers_correctly() {
        // Act
        logger.info("First message");
        logger.info("Second message");
        logger.info("Third message");

        // Assert
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        then(transport).should(times(3)).sendLog(captor.capture());

        var capturedEntries = captor.getAllValues();
        assertThat(capturedEntries.get(0).sequenceNumber()).isEqualTo(1L);
        assertThat(capturedEntries.get(1).sequenceNumber()).isEqualTo(2L);
        assertThat(capturedEntries.get(2).sequenceNumber()).isEqualTo(3L);
    }

    @Test
    void should_handle_console_output_when_enabled() {
        // Arrange
        var configWithConsole =
                new LogdashConfig(
                        "test-key", "https://api.logdash.io", true, false, 3, 1000L, 5000L, 10000L, 100);
        var loggerWithConsole = new LogdashLogger(configWithConsole, transport);

        // Act & Assert
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            loggerWithConsole.info("Console output test");
                        });
    }

    @Test
    void should_handle_empty_context() {
        // Act
        logger.info("Empty context", Map.of());

        // Assert
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        then(transport).should().sendLog(captor.capture());

        LogEntry captured = captor.getValue();
        assertThat(captured.message()).isEqualTo("Empty context");
    }

    @Test
    void should_handle_special_characters_in_message() {
        // Act
        logger.info("Message with \"quotes\" and \n newlines");

        // Assert
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        then(transport).should().sendLog(captor.capture());

        LogEntry captured = captor.getValue();
        assertThat(captured.message()).contains("quotes");
        assertThat(captured.message()).contains("newlines");
    }
}
