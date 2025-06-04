package io.logdash.sdk.transport;

import io.logdash.sdk.config.LogdashConfig;
import io.logdash.sdk.log.LogEntry;
import io.logdash.sdk.metrics.MetricEntry;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * No-operation transport that outputs observability data to console only.
 *
 * <p>This transport is used as a fallback when no API key is provided or when HTTP transport
 * initialization fails. It provides local visibility into logs and metrics without sending data to
 * external services.
 *
 * <p>Useful for development, testing, and scenarios where external connectivity is not available or
 * desired.
 */
public final class NoOpTransport implements LogdashTransport {

    private static final DateTimeFormatter CONSOLE_FORMATTER = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";

    private final LogdashConfig config;
    private volatile boolean closed = false;

    /**
     * Creates a new NoOp transport with the specified configuration.
     *
     * @param config the configuration for console output behavior
     */
    public NoOpTransport(LogdashConfig config) {
        this.config = config;
        if (config.enableVerboseLogging()) {
            System.out.println("Logdash NoOp transport initialized (console-only mode)");
        }
    }

    @Override
    public CompletableFuture<Void> sendLog(LogEntry logEntry) {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }

        if (config.enableConsoleOutput()) {
            printLogToConsole(logEntry);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> sendMetric(MetricEntry metricEntry) {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }

        if (config.enableConsoleOutput()) {
            printMetricToConsole(metricEntry);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Optional<CompletableFuture<Void>> shutdown() {
        return Optional.empty();
    }

    @Override
    public void close() {
        closed = true;
        if (config.enableVerboseLogging()) {
            System.out.println("Logdash NoOp transport closed");
        }
    }

    private void printLogToConsole(LogEntry logEntry) {
        var timestamp = OffsetDateTime.now().format(CONSOLE_FORMATTER);
        System.out.printf(
                "%s [%sLOG%s] %s: %s (seq=%d)%n",
                timestamp,
                CYAN,
                RESET,
                logEntry.level().getValue().toUpperCase(Locale.ENGLISH),
                logEntry.message(),
                logEntry.sequenceNumber());
    }

    private void printMetricToConsole(MetricEntry metricEntry) {
        var timestamp = OffsetDateTime.now().format(CONSOLE_FORMATTER);
        System.out.printf(
                "%s [%sMETRIC%s] %s %s = %s%n",
                timestamp,
                YELLOW,
                RESET,
                metricEntry.operation().getValue().toUpperCase(Locale.ENGLISH),
                metricEntry.name(),
                metricEntry.value());
    }
}
