package io.logdash.sdk.transport;

import io.logdash.sdk.config.LogdashConfig;
import io.logdash.sdk.log.LogEntry;
import io.logdash.sdk.log.LogLevel;
import io.logdash.sdk.metrics.MetricEntry;
import io.logdash.sdk.metrics.MetricType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpTransportTest {

    private NoOpTransport transport;
    private LogdashConfig config;
    private ByteArrayOutputStream capturedOutput;
    private PrintStream originalOut;

    private static String stripAnsiCodes(String input) {
        return input.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    @BeforeEach
    void setUp() {
        capturedOutput = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(capturedOutput));

        config =
                LogdashConfig.builder()
                        .apiKey("test-key")
                        .enableConsoleOutput(true)
                        .enableVerboseLogging(true)
                        .build();
        transport = new NoOpTransport(config);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        if (transport != null) {
            transport.close();
        }
    }

    @Test
    void should_initialize_with_verbose_logging() {
        // Assert
        assertThat(capturedOutput.toString()).contains("NoOp transport initialized");
    }

    @Test
    void should_print_log_to_console_when_enabled() throws Exception {
        // Arrange
        var logEntry = new LogEntry("Test message", LogLevel.INFO, 1L);

        // Act
        var future = transport.sendLog(logEntry);

        // Assert
        assertThat(future).succeedsWithin(100, TimeUnit.MILLISECONDS);
        var output = stripAnsiCodes(capturedOutput.toString());
        assertThat(output).contains("[LOG]");
        assertThat(output).contains("INFO");
        assertThat(output).contains("Test message");
        assertThat(output).contains("seq=1");
    }

    @Test
    void should_print_metric_to_console_when_enabled() throws Exception {
        // Arrange
        var metricEntry = new MetricEntry("test_metric", 42, MetricType.SET);

        // Act
        var future = transport.sendMetric(metricEntry);

        // Assert
        assertThat(future).succeedsWithin(100, TimeUnit.MILLISECONDS);
        var output = stripAnsiCodes(capturedOutput.toString());
        assertThat(output).contains("[METRIC]");
        assertThat(output).contains("SET");
        assertThat(output).contains("test_metric");
        assertThat(output).contains("42");
    }

    @Test
    void should_not_print_when_console_output_disabled() throws Exception {
        // Arrange
        var configWithoutConsole =
                LogdashConfig.builder()
                        .apiKey("test-key")
                        .enableConsoleOutput(false)
                        .enableVerboseLogging(false)
                        .build();
        var silentTransport = new NoOpTransport(configWithoutConsole);

        var logEntry = new LogEntry("Test message", LogLevel.INFO, 1L);
        var metricEntry = new MetricEntry("test_metric", 42, MetricType.SET);

        // Act
        var logFuture = silentTransport.sendLog(logEntry);
        var metricFuture = silentTransport.sendMetric(metricEntry);

        // Assert
        assertThat(logFuture).succeedsWithin(100, TimeUnit.MILLISECONDS);
        assertThat(metricFuture).succeedsWithin(100, TimeUnit.MILLISECONDS);

        // Only initialization messages should be present (none in this case)
        var output = capturedOutput.toString();
        assertThat(output).doesNotContain("[LOG]");
        assertThat(output).doesNotContain("[METRIC]");

        silentTransport.close();
    }

    @Test
    void should_handle_operations_after_close() throws Exception {
        // Arrange
        transport.close();
        var logEntry = new LogEntry("Test message", LogLevel.INFO, 1L);
        var metricEntry = new MetricEntry("test_metric", 42, MetricType.SET);

        // Act
        var logFuture = transport.sendLog(logEntry);
        var metricFuture = transport.sendMetric(metricEntry);

        // Assert
        assertThat(logFuture).succeedsWithin(100, TimeUnit.MILLISECONDS);
        assertThat(metricFuture).succeedsWithin(100, TimeUnit.MILLISECONDS);
        assertThat(capturedOutput.toString()).contains("transport closed");
    }

    @Test
    void should_handle_all_log_levels() throws Exception {
        // Act & Assert
        for (LogLevel level : LogLevel.values()) {
            var logEntry = new LogEntry("Test " + level, level, 1L);
            var future = transport.sendLog(logEntry);
            assertThat(future).succeedsWithin(100, TimeUnit.MILLISECONDS);
        }

        var output = capturedOutput.toString();
        for (LogLevel level : LogLevel.values()) {
            assertThat(output).contains(level.getValue().toUpperCase());
        }
    }

    @Test
    void should_handle_all_metric_types() throws Exception {
        // Act & Assert
        for (MetricType type : MetricType.values()) {
            var metricEntry = new MetricEntry("test_" + type, 42, type);
            var future = transport.sendMetric(metricEntry);
            assertThat(future).succeedsWithin(100, TimeUnit.MILLISECONDS);
        }

        var output = capturedOutput.toString();
        for (MetricType type : MetricType.values()) {
            assertThat(output).contains(type.getValue().toUpperCase());
        }
    }

    @Test
    void should_handle_special_characters_in_output() throws Exception {
        // Arrange
        var logEntry = new LogEntry("Message with émojis 🚀 and quotes \"test\"", LogLevel.INFO, 1L);
        var metricEntry = new MetricEntry("metric/with-special@chars", 3.14, MetricType.SET);

        // Act
        transport.sendLog(logEntry);
        transport.sendMetric(metricEntry);

        // Assert
        var output = capturedOutput.toString();
        assertThat(output).contains("émojis 🚀");
        assertThat(output).contains("quotes \"test\"");
        assertThat(output).contains("metric/with-special@chars");
    }

    @Test
    void should_handle_concurrent_operations() throws Exception {
        // Arrange
        var futures = new java.util.ArrayList<java.util.concurrent.CompletableFuture<Void>>();

        // Act
        for (int i = 0; i < 10; i++) {
            var logEntry = new LogEntry("Concurrent log " + i, LogLevel.INFO, i);
            var metricEntry = new MetricEntry("concurrent_metric_" + i, i, MetricType.SET);

            futures.add(transport.sendLog(logEntry));
            futures.add(transport.sendMetric(metricEntry));
        }

        // Assert
        var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        assertThat(allFutures).succeedsWithin(1, TimeUnit.SECONDS);
    }
}
