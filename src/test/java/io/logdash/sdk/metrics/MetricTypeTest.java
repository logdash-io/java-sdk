package io.logdash.sdk.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricTypeTest {

    @Test
    void should_have_correct_values() {
        assertThat(MetricType.SET.getValue()).isEqualTo("set");
        assertThat(MetricType.MUTATE.getValue()).isEqualTo("change");
    }

    @Test
    void should_return_correct_string_representation() {
        assertThat(MetricType.SET.toString()).hasToString("set");
        assertThat(MetricType.MUTATE.toString()).hasToString("change");
    }

    @Test
    void should_have_all_expected_types() {
        var types = MetricType.values();
        assertThat(types)
                .hasSize(2)
                .contains(MetricType.SET, MetricType.MUTATE);
    }
}
