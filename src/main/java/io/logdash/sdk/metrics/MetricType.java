package io.logdash.sdk.metrics;

/**
 * Defines the types of operations that can be performed on metrics in Logdash.
 *
 * <p>These operations determine how metric values are processed by the Logdash platform:
 *
 * <ul>
 *   <li>{@link #SET} - Replaces the current metric value with the provided value
 *   <li>{@link #MUTATE} - Modifies the current metric value by adding the provided delta
 * </ul>
 */
public enum MetricType {
    /**
     * Set operation - replaces the current metric value with the provided value. Use for gauge-type
     * metrics where you want to record absolute values.
     */
    SET("set"),

    /**
     * Mutate operation - modifies the current metric value by the provided delta. Use for
     * counter-type metrics where you want to increment/decrement values.
     */
    MUTATE("change");

    private final String value;

    MetricType(String value) {
        this.value = value;
    }

    /**
     * Returns the string value used in the Logdash API.
     *
     * @return the API string representation of this metric operation
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
