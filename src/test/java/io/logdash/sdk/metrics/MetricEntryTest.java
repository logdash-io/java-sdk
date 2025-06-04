package io.logdash.sdk.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricEntryTest {

    @Test
    void should_create_metric_entry_with_valid_parameters() {
        // Act
        var metricEntry = new MetricEntry("cpu_usage", 75.5, MetricType.SET);

        // Assert
        assertThat(metricEntry.name()).isEqualTo("cpu_usage");
        assertThat(metricEntry.value()).isEqualTo(75.5);
        assertThat(metricEntry.operation()).isEqualTo(MetricType.SET);
    }

    @Test
    void should_create_metric_entry_with_integer_value() {
        // Act
        var metricEntry = new MetricEntry("request_count", 42, MetricType.CHANGE);

        // Assert
        assertThat(metricEntry.name()).isEqualTo("request_count");
        assertThat(metricEntry.value()).isEqualTo(42);
        assertThat(metricEntry.operation()).isEqualTo(MetricType.CHANGE);
    }

    @Test
    void should_throw_exception_for_null_name() {
        assertThatThrownBy(() ->
                new MetricEntry(null, 42, MetricType.SET)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Metric name cannot be null or blank");
    }

    @Test
    void should_throw_exception_for_blank_name() {
        assertThatThrownBy(() ->
                new MetricEntry("   ", 42, MetricType.SET)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Metric name cannot be null or blank");
    }

    @Test
    void should_throw_exception_for_null_value() {
        assertThatThrownBy(() ->
                new MetricEntry("metric", null, MetricType.SET)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Metric value cannot be null");
    }

    @Test
    void should_throw_exception_for_null_operation() {
        assertThatThrownBy(() ->
                new MetricEntry("metric", 42, null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Metric operation cannot be null");
    }

    @Test
    void should_handle_negative_values() {
        // Act
        var metricEntry = new MetricEntry("temperature_change", -5.2, MetricType.CHANGE);

        // Assert
        assertThat(metricEntry.value()).isEqualTo(-5.2);
    }

    @Test
    void should_handle_zero_values() {
        // Act
        var metricEntry = new MetricEntry("reset_counter", 0, MetricType.SET);

        // Assert
        assertThat(metricEntry.value()).isEqualTo(0);
    }
}
