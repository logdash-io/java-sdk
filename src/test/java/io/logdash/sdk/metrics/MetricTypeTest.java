package io.logdash.sdk.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricTypeTest {

    @Test
    void should_have_correct_values() {
        assertThat(MetricType.SET.getValue()).isEqualTo("set");
        assertThat(MetricType.CHANGE.getValue()).isEqualTo("change");
    }

    @Test
    void should_return_correct_string_representation() {
        assertThat(MetricType.SET.toString()).isEqualTo("set");
        assertThat(MetricType.CHANGE.toString()).isEqualTo("change");
    }

    @Test
    void should_have_all_expected_types() {
        var types = MetricType.values();
        assertThat(types).hasSize(2);
        assertThat(types).contains(MetricType.SET, MetricType.CHANGE);
    }
}
