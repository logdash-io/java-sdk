package io.logdash.sdk.transport;

import io.logdash.sdk.log.LogEntry;
import io.logdash.sdk.metrics.MetricEntry;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Transport interface for sending observability data to Logdash platform.
 *
 * <p>Implementations handle the actual delivery mechanism for logs and metrics, whether through
 * HTTP, console output, or other means. All operations are asynchronous to avoid blocking the
 * application.
 */
public interface LogdashTransport {

    /**
     * Sends a log entry asynchronously to the transport destination.
     *
     * @param logEntry the log entry to send
     * @return a future that completes when the operation finishes
     */
    CompletableFuture<Void> sendLog(LogEntry logEntry);

    /**
     * Sends a metric entry asynchronously to the transport destination.
     *
     * @param metricEntry the metric entry to send
     * @return a future that completes when the operation finishes
     */
    CompletableFuture<Void> sendMetric(MetricEntry metricEntry);

    /**
     * Initiates graceful shutdown of the transport.
     *
     * <p>Implementations should ensure all pending operations complete before the returned future
     * completes.
     *
     * @return an optional future for shutdown completion, empty if no cleanup needed
     */
    default Optional<CompletableFuture<Void>> shutdown() {
        return Optional.empty();
    }

    /**
     * Immediately closes the transport and releases all resources.
     *
     * <p>This method should be idempotent and safe to call multiple times.
     */
    void close();
}
