package io.logdash.sdk.metrics;

import io.logdash.sdk.config.LogdashConfig;
import io.logdash.sdk.transport.LogdashTransport;

/**
 * Primary interface for sending custom metrics to Logdash platform.
 *
 * <p>Provides convenient methods for common metric operations including counters, gauges, and
 * custom measurements. All operations are asynchronous and thread-safe.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * metrics.set("active_users", 150);
 * metrics.mutate("response_time_ms", -50);
 * }</pre>
 */
public final class LogdashMetrics {

    private final LogdashConfig config;
    private final LogdashTransport transport;

    /**
     * Creates a new metrics instance with the specified configuration and transport.
     *
     * <p><strong>Note:</strong> This constructor is internal to the SDK. Use {@link
     * io.logdash.sdk.Logdash#metrics()} to obtain an instance.
     *
     * @param config    the SDK configuration
     * @param transport the transport for sending metrics
     */
    public LogdashMetrics(LogdashConfig config, LogdashTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    /**
     * Sets a metric to a specific absolute value.
     *
     * <p>Use this for gauge-type metrics where you want to record the current state or level of
     * something.
     *
     * @param name  the metric name (e.g., "memory_usage_mb")
     * @param value the absolute value to set
     */
    public void set(String name, Number value) {
        var metric = new MetricEntry(name, value, MetricType.SET);
        sendMetric(metric);
    }

    /**
     * Changes a metric by the specified delta value.
     *
     * <p>Positive values increase the metric, negative values decrease it. This is the most flexible
     * method for counter-type metrics.
     *
     * @param name  the metric name
     * @param delta the change amount (positive or negative)
     */
    public void mutate(String name, Number delta) {
        var metric = new MetricEntry(name, delta, MetricType.MUTATE);
        sendMetric(metric);
    }

    private void sendMetric(MetricEntry metric) {
        if (config.enableVerboseLogging()) {
            System.out.printf(
                    "Metric: %s %s %s%n", metric.operation().getValue(), metric.name(), metric.value());
        }
        transport.sendMetric(metric);
    }
}
