package io.logdash.sdk.performance;

import io.logdash.sdk.Logdash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class PerformanceTest {

    @Test
    void should_handle_large_message_payloads() {
        try (var logdash = Logdash.builder()
                .apiKey("large-payload-test")
                .enableConsoleOutput(false)
                .build()) {

            var largeMessage = "A".repeat(100_000); // 100KB message

            assertThatNoException()
                    .isThrownBy(
                            () -> {
                                logdash.logger().info(largeMessage);
                                logdash.flush();
                            });
        }
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void should_maintain_performance_under_mixed_load() throws InterruptedException {
        try (var logdash =
                     Logdash.builder()
                             .apiKey("mixed-load-test")
                             .enableConsoleOutput(false)
                             .maxConcurrentRequests(20)
                             .build()) {

            var executor = Executors.newFixedThreadPool(10);
            var futures = new ArrayList<java.util.concurrent.CompletableFuture<Void>>();

            // Mixed workload: logs and metrics
            for (int i = 0; i < 50; i++) {
                final int iteration = i;

                var future =
                        CompletableFuture.runAsync(
                                () -> {
                                    // Logs
                                    logdash.logger().info("Mixed load log " + iteration);
                                    logdash.logger().debug("Debug info " + iteration);

                                    // Metrics
                                    logdash.metrics().mutate("operations", 1);
                                    logdash.metrics().set("current_batch", iteration);
                                    logdash.metrics().mutate("temperature", Math.random() * 10 - 5);
                                },
                                executor);

                futures.add(future);
            }

            var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            assertThatNoException()
                    .isThrownBy(
                            () -> {
                                allFutures.get(10, TimeUnit.SECONDS);
                                logdash.flush();
                            });

            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
