package io.logdash.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class LogdashTest {

    @Test
    void should_create_sdk_with_valid_configuration() {
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            try (var sdk =
                                         Logdash.builder()
                                                 .apiKey("test-api-key")
                                                 .baseUrl("https://api.logdash.io")
                                                 .build()) {

                                assertThat(sdk.logger()).isNotNull();
                                assertThat(sdk.metrics()).isNotNull();
                                assertThat(sdk.config().apiKey()).isEqualTo("test-api-key");
                                assertThat(sdk.config().baseUrl()).isEqualTo("https://api.logdash.io");
                            }
                        });
    }

    @Test
    void should_create_sdk_with_default_configuration() {
        // Act
        try (var sdk = Logdash.create("test-key")) {
            // Assert
            assertThat(sdk.config().apiKey()).isEqualTo("test-key");
            assertThat(sdk.config().baseUrl()).isEqualTo("https://api.logdash.io");
            assertThat(sdk.config().enableConsoleOutput()).isTrue();
            assertThat(sdk.config().enableVerboseLogging()).isFalse();
        }
    }

    @Test
    void should_create_sdk_without_api_key_and_use_noop_transport() {
        // Act
        try (var sdk = Logdash.builder()
                .apiKey("")
                .enableVerboseLogging(true)
                .build()) {

            // Assert
            assertThat(sdk.logger()).isNotNull();
            assertThat(sdk.metrics()).isNotNull();
        }
    }

    @Test
    void should_throw_exception_when_accessing_closed_sdk() {
        // Arrange
        var sdk = Logdash.create("test-key");
        sdk.close();

        // Act & Assert
        assertThatThrownBy(sdk::logger)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");

        assertThatThrownBy(sdk::metrics)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void should_handle_flush_operation() {
        // Arrange
        try (var sdk = Logdash.builder()
                .apiKey("test-key")
                .enableVerboseLogging(false)
                .build()) {

            sdk.logger().info("Test message");
            sdk.metrics().set("test_metric", 42);

            // Act & Assert
            assertThatNoException().isThrownBy(sdk::flush);
        }
    }

    @Test
    void should_configure_all_builder_options() {
        // Act
        try (var sdk =
                     Logdash.builder()
                             .apiKey("custom-key")
                             .baseUrl("https://custom.logdash.io")
                             .enableConsoleOutput(false)
                             .enableVerboseLogging(true)
                             .maxRetries(5)
                             .retryDelayMs(2000L)
                             .requestTimeoutMs(20000L)
                             .shutdownTimeoutMs(15000L)
                             .maxConcurrentRequests(50)
                             .build()) {

            // Assert
            var config = sdk.config();
            assertThat(config.apiKey()).isEqualTo("custom-key");
            assertThat(config.baseUrl()).isEqualTo("https://custom.logdash.io");
            assertThat(config.enableConsoleOutput()).isFalse();
            assertThat(config.enableVerboseLogging()).isTrue();
            assertThat(config.maxRetries()).isEqualTo(5);
            assertThat(config.retryDelayMs()).isEqualTo(2000L);
            assertThat(config.requestTimeoutMs()).isEqualTo(20000L);
            assertThat(config.shutdownTimeoutMs()).isEqualTo(15000L);
            assertThat(config.maxConcurrentRequests()).isEqualTo(50);
        }
    }

    @Test
    void should_allow_multiple_close_calls_safely() {
        // Arrange
        var sdk = Logdash.create("test-key");

        // Act & Assert
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            sdk.close();
                            sdk.close(); // Second close should be safe
                        });
    }

    @Test
    void should_work_with_try_with_resources() {
        // Act & Assert
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            try (var sdk = Logdash.create("test-key")) {
                                sdk.logger().info("Test message", Map.of("key", "value"));
                                sdk.metrics().increment("test_counter");
                            }
                            // Auto-close should work without issues
                        });
    }

    @Test
    void should_handle_builder_with_all_default_values() {
        try (var sdk = Logdash.builder().build()) {
            var config = sdk.config();

            assertThat(config.apiKey()).isNull();
            assertThat(config.baseUrl()).isEqualTo("https://api.logdash.io");
            assertThat(config.enableConsoleOutput()).isTrue();
            assertThat(config.enableVerboseLogging()).isFalse();
            assertThat(config.maxRetries()).isEqualTo(3);
            assertThat(config.retryDelayMs()).isEqualTo(500L);
            assertThat(config.requestTimeoutMs()).isEqualTo(15000L);
            assertThat(config.shutdownTimeoutMs()).isEqualTo(10000L);
            assertThat(config.maxConcurrentRequests()).isEqualTo(20);
        }
    }

    @Test
    void should_handle_runtime_shutdown_hook() {
        // Test that shutdown hook is properly registered and removed
        var sdk = Logdash.create("test-key");

        // Verify SDK is operational
        assertThat(sdk.logger()).isNotNull();
        assertThat(sdk.metrics()).isNotNull();

        // Close should not throw exception
        assertThatNoException().isThrownBy(sdk::close);
    }

    @Test
    void should_maintain_thread_safety_during_concurrent_access() throws InterruptedException {
        try (var sdk = Logdash.builder()
                .apiKey("concurrent-test")
                .enableConsoleOutput(false)
                .build()) {

            var executor = Executors.newFixedThreadPool(10);
            var latch = new CountDownLatch(50);
            var exceptions = new ConcurrentLinkedQueue<Exception>();

            // Submit concurrent operations
            for (int i = 0; i < 50; i++) {
                final int iteration = i;
                executor.submit(
                        () -> {
                            try {
                                sdk.logger().info("Concurrent message " + iteration);
                                sdk.metrics().increment("concurrent_counter");
                            } catch (Exception e) {
                                exceptions.add(e);
                            } finally {
                                latch.countDown();
                            }
                        });
            }

            // Wait for all operations to complete
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exceptions).isEmpty();

            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void should_handle_flush_with_no_pending_operations() {
        try (var sdk = Logdash.builder()
                .apiKey("test-key")
                .enableConsoleOutput(false)
                .build()) {

            // Flush when no operations are pending
            assertThatNoException().isThrownBy(sdk::flush);
        }
    }
}
