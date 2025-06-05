package io.logdash.sdk.error;

import io.logdash.sdk.Logdash;
import io.logdash.sdk.config.LogdashConfig;
import io.logdash.sdk.exception.LogdashException;
import io.logdash.sdk.log.LogEntry;
import io.logdash.sdk.log.LogLevel;
import io.logdash.sdk.metrics.MetricEntry;
import io.logdash.sdk.metrics.MetricType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class ErrorHandlingTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void should_handle_invalid_api_keys(String apiKey) {
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            try (var logdash = Logdash.builder().apiKey(apiKey).build()) {

                                logdash.logger().info("Test");
                                logdash.metrics().mutate("counter", 1);
                            }
                        });
    }

    @Test
    void should_validate_config_parameters() {
        assertThatThrownBy(() ->
                LogdashConfig.builder().maxRetries(-1).build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Max retries cannot be negative");

        assertThatThrownBy(() ->
                LogdashConfig.builder().requestTimeoutMs(0).build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Request timeout must be positive");

        assertThatThrownBy(() ->
                LogdashConfig.builder().maxConcurrentRequests(0).build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Max concurrent requests must be positive");
    }

    @Test
    void should_handle_extreme_values() {
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            try (var logdash =
                                         Logdash.builder()
                                                 .apiKey("test")
                                                 .requestTimeoutMs(Long.MAX_VALUE)
                                                 .retryDelayMs(Long.MAX_VALUE)
                                                 .maxRetries(Integer.MAX_VALUE)
                                                 .build()) {

                                logdash.logger().info("Extreme config test");
                            }
                        });
    }

    @Test
    void should_handle_resource_exhaustion_scenarios() {
        try (var logdash =
                     Logdash.builder().apiKey("test").maxConcurrentRequests(1).requestTimeoutMs(100L).build()) {

            for (int i = 0; i < 100; i++) {
                logdash.logger().info("Stress test " + i);
                logdash.metrics().mutate("stress_counter", 1);
            }

            assertThatNoException().isThrownBy(logdash::flush);
        }
    }

    @Test
    void should_handle_malformed_data_gracefully() {
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            var entry = new LogEntry("\u0000\u001F\uFFFF", LogLevel.INFO, 1L);
                        });

        assertThatNoException()
                .isThrownBy(
                        () -> {
                            var metric = new MetricEntry("test\n\r\t", Double.NaN, MetricType.SET);
                        });
    }

    @Test
    void should_maintain_thread_safety_under_stress() throws InterruptedException {
        try (var logdash =
                     Logdash.builder().apiKey("thread-safety-test").enableConsoleOutput(false).build()) {

            var executor = Executors.newFixedThreadPool(20);
            var latch = new CountDownLatch(200);
            var exceptions = new ConcurrentLinkedQueue<Exception>();

            for (int i = 0; i < 200; i++) {
                final int iteration = i;
                executor.submit(
                        () -> {
                            try {
                                logdash.logger().info("Thread safety test " + iteration);
                                logdash.metrics().mutate("thread_counter", 1);
                                logdash.metrics().set("thread_id", getThreadId());
                            } catch (Exception e) {
                                exceptions.add(e);
                            } finally {
                                latch.countDown();
                            }
                        });
            }

            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(exceptions).isEmpty();

            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @SuppressWarnings("deprecation")
    private long getThreadId() {
        return Thread.currentThread().getId();
    }
}
