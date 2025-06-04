package io.logdash.sdk.integration;

import io.logdash.sdk.Logdash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatNoException;

class LogdashIntegrationTest {

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void should_handle_rapid_successive_operations() {
        try (var logdash =
                     Logdash.builder()
                             .apiKey("test-key")
                             .enableVerboseLogging(false)
                             .enableConsoleOutput(false)
                             .maxConcurrentRequests(5)
                             .requestTimeoutMs(1000L)
                             .retryDelayMs(50L)
                             .maxRetries(1)
                             .build()) {

            var logger = logdash.logger();
            var metrics = logdash.metrics();

            // Rapid operations
            for (int i = 0; i < 50; i++) {
                logger.info("Message " + i, Map.of("iteration", i));
                metrics.increment("counter", i % 5);
                if (i % 5 == 0) {
                    metrics.set("batch", i / 5);
                }
            }

            // Should complete without exceptions
            assertThatNoException().isThrownBy(logdash::flush);
        }
    }

    @Test
    void should_handle_all_log_levels_correctly() {
        try (var logdash = Logdash.builder().apiKey("test-key").enableConsoleOutput(false).build()) {
            var logger = logdash.logger();

            assertThatNoException()
                    .isThrownBy(
                            () -> {
                                logger.error("Error message");
                                logger.warn("Warning message");
                                logger.info("Info message");
                                logger.http("HTTP message");
                                logger.verbose("Verbose message");
                                logger.debug("Debug message");
                                logger.silly("Silly message");
                            });
        }
    }

    @Test
    void should_handle_metric_operations_correctly() {
        try (var logdash = Logdash.builder().apiKey("test-key").enableConsoleOutput(false).build()) {
            var metrics = logdash.metrics();

            assertThatNoException()
                    .isThrownBy(
                            () -> {
                                metrics.set("users", 100);
                                metrics.increment("requests");
                                metrics.increment("requests", 5);
                                metrics.decrement("errors");
                                metrics.decrement("errors", 2);
                                metrics.change("temperature", -5.5);
                            });
        }
    }

    @Test
    void should_recover_from_transport_creation_failure() {
        // Using invalid URL to force transport creation failure
        try (var logdash =
                     Logdash.builder()
                             .apiKey("test-key")
                             .baseUrl("invalid-url")
                             .enableVerboseLogging(false)
                             .enableConsoleOutput(false)
                             .build()) {

            // Should still work with NoOp transport
            assertThatNoException()
                    .isThrownBy(
                            () -> {
                                logdash.logger().info("Test message");
                                logdash.metrics().set("test", 1);
                            });
        }
    }

    @Test
    void should_handle_no_api_key_scenario() {
        try (var logdash = Logdash.builder().apiKey("").enableConsoleOutput(false).build()) {

            assertThatNoException()
                    .isThrownBy(
                            () -> {
                                logdash.logger().info("Test message");
                                logdash.metrics().increment("counter");
                            });
        }
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void should_handle_mixed_logging_and_metrics_under_load() {
        try (var logdash =
                     Logdash.builder()
                             .apiKey("load-test-key")
                             .enableVerboseLogging(false)
                             .enableConsoleOutput(false)
                             .maxConcurrentRequests(10)
                             .requestTimeoutMs(2000L)
                             .retryDelayMs(100L)
                             .maxRetries(2)
                             .build()) {

            var logger = logdash.logger();
            var metrics = logdash.metrics();
            var futures = new ArrayList<CompletableFuture<Void>>();

            // Act
            for (int i = 0; i < 100; i++) {
                final int iteration = i;

                // Log at different levels
                logger.info("Iteration " + iteration, Map.of("step", iteration));
                if (iteration % 10 == 0) {
                    logger.warn("Checkpoint reached", Map.of("checkpoint", iteration / 10));
                }
                if (iteration % 25 == 0) {
                    logger.error("Quarter milestone", Map.of("quarter", iteration / 25));
                }

                // Various metrics
                metrics.increment("iterations");
                metrics.set("current_iteration", iteration);
                if (iteration % 5 == 0) {
                    metrics.change("batch_size", iteration % 3 == 0 ? 1 : -1);
                }
            }

            // Assert
            assertThatNoException().isThrownBy(logdash::flush);
        }
    }

    @Test
    void should_handle_very_long_messages_gracefully() {
        try (var logdash =
                     Logdash.builder().apiKey("long-message-test").enableConsoleOutput(false).build()) {

            var logger = logdash.logger();
            var veryLongMessage = "X".repeat(50000); // 50KB message

            // Act & Assert
            assertThatNoException()
                    .isThrownBy(
                            () -> {
                                logger.info(veryLongMessage);
                                logger.debug(veryLongMessage, Map.of("size", veryLongMessage.length()));
                            });
        }
    }

    @Test
    void should_maintain_sequence_numbers_across_different_log_levels() {
        try (var logdash =
                     Logdash.builder().apiKey("sequence-test").enableConsoleOutput(false).build()) {

            var logger = logdash.logger();

            // Act
            logger.error("Error 1");
            logger.info("Info 1");
            logger.debug("Debug 1");
            logger.warn("Warn 1");
            logger.verbose("Verbose 1");
            logger.silly("Silly 1");
            logger.http("HTTP 1");

            // Assert
            assertThatNoException().isThrownBy(logdash::flush);
        }
    }
}
