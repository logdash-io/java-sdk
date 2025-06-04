package io.logdash.sdk.metrics;

/**
 * Immutable data container representing a metric entry to be sent to Logdash.
 *
 * <p>A metric entry consists of:
 *
 * <ul>
 *   <li>A unique name identifying the metric (e.g., "http_requests_total")
 *   <li>A numeric value representing the measurement
 *   <li>An operation type defining how the metric should be processed
 * </ul>
 *
 * @param name      the metric identifier, must not be null or blank
 * @param value     the numeric value, must not be null
 * @param operation the type of operation to perform, must not be null
 */
public record MetricEntry(String name, Number value, MetricType operation) {

    /**
     * Validates the metric entry parameters during construction.
     *
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    public MetricEntry {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Metric name cannot be null or blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("Metric value cannot be null");
        }
        if (operation == null) {
            throw new IllegalArgumentException("Metric operation cannot be null");
        }
    }
}
