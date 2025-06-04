package io.logdash.sdk.metrics;

import io.logdash.sdk.config.LogdashConfig;
import io.logdash.sdk.transport.LogdashTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class LogdashMetricsTest {

    @Mock
    private LogdashTransport transport;

    private LogdashConfig config;
    private LogdashMetrics metrics;

    @BeforeEach
    void setUp() {
        config = new LogdashConfig(
                "test-key",
                "https://api.logdash.io",
                false,
                false,
                3,
                1000L,
                5000L,
                10000L,
                100
        );

        given(transport.sendMetric(any())).willReturn(CompletableFuture.completedFuture(null));

        metrics = new LogdashMetrics(config, transport);
    }

    @Test
    void should_send_set_metric() {
        // Act
        metrics.set("active_users", 100);

        // Assert
        then(transport).should().sendMetric(any(MetricEntry.class));
    }

    @Test
    void should_send_increment_metric() {
        // Act
        metrics.increment("page_views");

        // Assert
        then(transport).should().sendMetric(any(MetricEntry.class));
    }

    @Test
    void should_send_increment_metric_with_custom_amount() {
        // Act
        metrics.increment("requests", 5);

        // Assert
        then(transport).should().sendMetric(any(MetricEntry.class));
    }

    @Test
    void should_send_change_metric() {
        // Act
        metrics.change("temperature", -5.5);

        // Assert
        then(transport).should().sendMetric(any(MetricEntry.class));
    }

    @Test
    void should_handle_decrement_operations() {
        // Act
        metrics.decrement("counter");
        metrics.decrement("errors", 5);

        // Assert
        then(transport).should(times(2)).sendMetric(any(MetricEntry.class));
    }

    @Test
    void should_handle_decimal_values_correctly() {
        // Act
        metrics.set("temperature", 23.5);
        metrics.change("pressure", -1.2);

        // Assert
        then(transport).should(times(2)).sendMetric(any(MetricEntry.class));
    }

    @Test
    void should_handle_negative_values() {
        // Act
        metrics.set("deficit", -100);
        metrics.change("altitude", -50.5);

        // Assert
        then(transport).should(times(2)).sendMetric(any(MetricEntry.class));
    }
}
