package io.logdash.sdk.transport;

import io.logdash.sdk.log.LogEntry;
import io.logdash.sdk.log.LogLevel;
import io.logdash.sdk.metrics.MetricEntry;
import io.logdash.sdk.metrics.MetricType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class LogdashTransportTest {

    @Test
    void should_define_contract_for_log_sending() {
        LogdashTransport transport = new TestTransport();

        var logEntry = new LogEntry("Test message", LogLevel.INFO, 1L);
        var future = transport.sendLog(logEntry);

        assertThat(future).isNotNull();
        assertThat(future).isInstanceOf(CompletableFuture.class);
    }

    @Test
    void should_define_contract_for_metric_sending() {
        LogdashTransport transport = new TestTransport();

        var metricEntry = new MetricEntry("test_metric", 42, MetricType.SET);
        var future = transport.sendMetric(metricEntry);

        assertThat(future).isNotNull();
        assertThat(future).isInstanceOf(CompletableFuture.class);
    }

    @Test
    void should_provide_optional_shutdown_method() {
        LogdashTransport transport = new TestTransport();

        var shutdownFuture = transport.shutdown();

        assertThat(shutdownFuture).isNotNull();
        assertThat(shutdownFuture).isInstanceOf(Optional.class);
    }

    private static class TestTransport implements LogdashTransport {
        @Override
        public CompletableFuture<Void> sendLog(LogEntry logEntry) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> sendMetric(MetricEntry metricEntry) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            // Test implementation
        }
    }
}
