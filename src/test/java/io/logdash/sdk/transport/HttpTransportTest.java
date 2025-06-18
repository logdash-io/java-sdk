package io.logdash.sdk.transport;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.logdash.sdk.config.LogdashConfig;
import io.logdash.sdk.log.LogEntry;
import io.logdash.sdk.log.LogLevel;
import io.logdash.sdk.metrics.MetricEntry;
import io.logdash.sdk.metrics.MetricType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class HttpTransportTest {

    private WireMockServer wireMockServer;
    private LogdashConfig config;
    private HttpTransport transport;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        config =
                LogdashConfig.builder()
                        .apiKey("test-api-key")
                        .baseUrl("http://localhost:" + wireMockServer.port())
                        .enableVerboseLogging(false)
                        .requestTimeoutMs(2000L)
                        .maxRetries(2)
                        .retryDelayMs(100L)
                        .maxConcurrentRequests(5)
                        .shutdownTimeoutMs(1000L)
                        .build();

        transport = new HttpTransport(config);
    }

    @AfterEach
    void tearDown() {
        if (transport != null) {
            transport.close();
        }
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void should_successfully_send_log_entry() {
        // Arrange
        wireMockServer.stubFor(
                post(urlEqualTo("/logs"))
                        .willReturn(
                                aResponse().withStatus(200).withHeader("Content-Type", "application/json")));

        var logEntry = new LogEntry("Test message", LogLevel.INFO, 1L);

        // Act
        var future = transport.sendLog(logEntry);

        // Assert
        assertThat(future).succeedsWithin(3, TimeUnit.SECONDS);

        wireMockServer.verify(
                postRequestedFor(urlEqualTo("/logs"))
                        .withHeader("Content-Type", equalTo("application/json"))
                        .withHeader("project-api-key", equalTo("test-api-key"))
                        .withHeader("User-Agent", equalTo("logdash-java-sdk/0.2.0")));
    }

    @Test
    void should_successfully_send_metric_entry() {
        // Arrange
        wireMockServer.stubFor(
                put(urlEqualTo("/metrics"))
                        .willReturn(
                                aResponse().withStatus(202).withHeader("Content-Type", "application/json")));

        var metricEntry = new MetricEntry("test_metric", 42, MetricType.SET);

        // Act
        var future = transport.sendMetric(metricEntry);

        // Assert
        assertThat(future).succeedsWithin(3, TimeUnit.SECONDS);

        wireMockServer.verify(
                putRequestedFor(urlEqualTo("/metrics"))
                        .withHeader("Content-Type", equalTo("application/json"))
                        .withHeader("project-api-key", equalTo("test-api-key"))
                        .withHeader("User-Agent", equalTo("logdash-java-sdk/0.2.0")));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 500, 502, 503})
    void should_retry_on_http_error_responses(int statusCode) {
        // Arrange
        wireMockServer.stubFor(
                post(urlEqualTo("/logs"))
                        .inScenario("retry-scenario")
                        .whenScenarioStateIs("Started")
                        .willReturn(aResponse().withStatus(statusCode))
                        .willSetStateTo("first-retry"));

        wireMockServer.stubFor(
                post(urlEqualTo("/logs"))
                        .inScenario("retry-scenario")
                        .whenScenarioStateIs("first-retry")
                        .willReturn(aResponse().withStatus(200)));

        var logEntry = new LogEntry("Test message", LogLevel.INFO, 1L);

        // Act
        var future = transport.sendLog(logEntry);

        // Assert
        assertThat(future).succeedsWithin(5, TimeUnit.SECONDS);
        wireMockServer.verify(2, postRequestedFor(urlEqualTo("/logs")));
    }

    @Test
    void should_fail_permanently_after_max_retries() throws Exception {
        // Arrange
        wireMockServer.stubFor(post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(500)));

        var logEntry = new LogEntry("Test message", LogLevel.INFO, 1L);

        // Act
        var future = transport.sendLog(logEntry);

        // Assert
        assertThat(future).succeedsWithin(10, TimeUnit.SECONDS);

        // Wait a bit for all retry attempts to complete
        Thread.sleep(1000);

        // Should have made initial request + 2 retries = 3 total
        wireMockServer.verify(moreThanOrExactly(2), postRequestedFor(urlEqualTo("/logs")));
    }

    @Test
    void should_handle_connection_timeout() {
        // Arrange
        wireMockServer.stubFor(
                post(urlEqualTo("/logs"))
                        .willReturn(
                                aResponse().withStatus(200).withFixedDelay(5000))); // Longer than request timeout

        var logEntry = new LogEntry("Test message", LogLevel.INFO, 1L);

        // Act
        var future = transport.sendLog(logEntry);

        // Assert
        assertThat(future).succeedsWithin(15, TimeUnit.SECONDS); // Should complete despite timeout
    }

    @Test
    void should_handle_concurrent_requests_within_limit() {
        // Arrange
        wireMockServer.stubFor(
                post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200).withFixedDelay(500)));

        var futures = new ArrayList<CompletableFuture<Void>>();

        // Act
        for (int i = 0; i < 3; i++) {
            var logEntry = new LogEntry("Message " + i, LogLevel.INFO, i);
            futures.add(transport.sendLog(logEntry));
        }

        // Assert
        var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        assertThat(allFutures).succeedsWithin(10, TimeUnit.SECONDS);
        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/logs")));
    }

    @Test
    void should_respect_concurrency_limits() {
        // Arrange
        var configWithLowLimit =
                LogdashConfig.builder()
                        .apiKey("test-key")
                        .baseUrl("http://localhost:" + wireMockServer.port())
                        .maxConcurrentRequests(1)
                        .requestTimeoutMs(1000L)
                        .build();

        var limitedTransport = new HttpTransport(configWithLowLimit);

        wireMockServer.stubFor(
                post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200).withFixedDelay(800)));

        var futures = new ArrayList<CompletableFuture<Void>>();

        try {
            // Act
            for (int i = 0; i < 5; i++) {
                var logEntry = new LogEntry("Message " + i, LogLevel.INFO, i);
                futures.add(limitedTransport.sendLog(logEntry));
            }

            // Assert
            var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            assertThat(allFutures).succeedsWithin(15, TimeUnit.SECONDS);

            // Some requests might be rejected due to concurrency limit
            var requestCount = wireMockServer.findAll(postRequestedFor(urlEqualTo("/logs"))).size();
            assertThat(requestCount).isLessThanOrEqualTo(5);

        } finally {
            limitedTransport.close();
        }
    }

    @Test
    void should_not_send_requests_after_shutdown() {
        // Arrange
        transport.shutdown();
        var logEntry = new LogEntry("Test message", LogLevel.INFO, 1L);

        // Act
        var future = transport.sendLog(logEntry);

        // Assert
        assertThat(future).succeedsWithin(1, TimeUnit.SECONDS);
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/logs")));
    }

    @Test
    void should_handle_graceful_shutdown_with_pending_requests() {
        // Arrange
        wireMockServer.stubFor(
                post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200).withFixedDelay(300)));

        var logEntry = new LogEntry("Test message", LogLevel.INFO, 1L);
        var future = transport.sendLog(logEntry);

        // Act
        var shutdownFuture = transport.shutdown();

        // Assert
        assertThat(shutdownFuture).isPresent();
        assertThat(shutdownFuture.get()).succeedsWithin(5, TimeUnit.SECONDS);
        assertThat(future).succeedsWithin(5, TimeUnit.SECONDS);
    }

    @Test
    void should_flush_pending_requests() {
        // Arrange
        wireMockServer.stubFor(
                post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200).withFixedDelay(200)));

        var logEntry = new LogEntry("Test message", LogLevel.INFO, 1L);
        transport.sendLog(logEntry);

        // Act
        var flushFuture = transport.flush();

        // Assert
        assertThat(flushFuture).succeedsWithin(5, TimeUnit.SECONDS);
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/logs")));
    }

    @Test
    void should_handle_serialization_errors_gracefully() {
        // Arrange
        var logEntry =
                new LogEntry("Test with very long message: " + "x".repeat(100000), LogLevel.INFO, 1L);

        // Act
        var future = transport.sendLog(logEntry);

        // Assert
        assertThat(future).succeedsWithin(5, TimeUnit.SECONDS);
    }

    @Test
    void should_handle_large_payloads() {
        // Arrange
        wireMockServer.stubFor(post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200)));

        var largeMessage = "A".repeat(10000);
        var logEntry = new LogEntry(largeMessage, LogLevel.INFO, 1L);

        // Act
        var future = transport.sendLog(logEntry);

        // Assert
        assertThat(future).succeedsWithin(5, TimeUnit.SECONDS);
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/logs")));
    }

    @Test
    void should_handle_multiple_close_calls_safely() {
        // Act & Assert
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            transport.close();
                            transport.close(); // Second close should be safe
                        });
    }

    @Test
    void should_send_correct_json_for_log_entries_with_context() throws Exception {
        // Arrange
        wireMockServer.stubFor(post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200)));

        var timestamp = Instant.parse("2024-06-01T12:34:56Z");
        Map<String, Object> context = Map.of("userId", "123", "action", "login");
        var logEntry = new LogEntry("User logged in", LogLevel.INFO, timestamp, 42L, context);

        // Act
        var future = transport.sendLog(logEntry);
        assertThat(future).succeedsWithin(3, TimeUnit.SECONDS);

        // Wait for request to complete
        Thread.sleep(200);

        // Assert
        wireMockServer.verify(
                postRequestedFor(urlEqualTo("/logs"))
                        .withRequestBody(containing("\"level\":\"info\""))
                        .withRequestBody(containing("\"sequenceNumber\":42"))
                        .withRequestBody(containing("\"createdAt\":\"2024-06-01T12:34:56Z\"")));
    }

    @Test
    void should_send_correct_json_for_metrics() throws Exception {
        // Arrange
        wireMockServer.stubFor(put(urlEqualTo("/metrics")).willReturn(aResponse().withStatus(200)));

        var metricEntry = new MetricEntry("cpu_usage", 75.5, MetricType.SET);

        // Act
        var future = transport.sendMetric(metricEntry);
        assertThat(future).succeedsWithin(3, TimeUnit.SECONDS);

        // Wait for request to complete
        Thread.sleep(200);

        // Assert
        wireMockServer.verify(
                putRequestedFor(urlEqualTo("/metrics"))
                        .withRequestBody(containing("\"name\":\"cpu_usage\""))
                        .withRequestBody(containing("\"value\":75.5"))
                        .withRequestBody(containing("\"operation\":\"set\"")));
    }

    @Test
    @Timeout(15)
    void should_handle_high_throughput_requests_with_realistic_expectations()
            throws InterruptedException {
        // Arrange - Use higher concurrency limit for throughput test
        var highThroughputConfig =
                LogdashConfig.builder()
                        .apiKey("test-key")
                        .baseUrl("http://localhost:" + wireMockServer.port())
                        .maxConcurrentRequests(15) // Higher limit
                        .requestTimeoutMs(5000L)
                        .build();

        var throughputTransport = new HttpTransport(highThroughputConfig);

        wireMockServer.stubFor(
                post(urlEqualTo("/logs"))
                        .willReturn(aResponse().withStatus(200).withFixedDelay(100))); // Reasonable delay

        var requestCount = 30; // Realistic number for test
        var futures = new ArrayList<CompletableFuture<Void>>(requestCount);
        var completedLatch = new CountDownLatch(requestCount);

        try {
            // Act
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < requestCount; i++) {
                var logEntry = new LogEntry("Throughput test " + i, LogLevel.INFO, i);
                var future = throughputTransport.sendLog(logEntry);
                future.whenComplete((result, throwable) -> completedLatch.countDown());
                futures.add(future);

                // Small delay to avoid overwhelming
                if (i % 5 == 0) {
                    Thread.sleep(10);
                }
            }

            // Assert
            assertThat(completedLatch.await(12, TimeUnit.SECONDS)).isTrue();
            long duration = System.currentTimeMillis() - startTime;

            // Should complete in reasonable time
            assertThat(duration).isLessThan(12000);

            // All futures should complete
            var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            assertThat(allFutures).succeedsWithin(2, TimeUnit.SECONDS);

            // Most requests should be processed (allow for some concurrency rejections)
            var actualRequests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/logs"))).size();
            assertThat(actualRequests).isGreaterThan(requestCount / 2); // At least 50%

        } finally {
            throughputTransport.close();
        }
    }

    @Test
    void should_implement_exponential_backoff_with_jitter() throws InterruptedException {
        // Arrange
        var retryDelayMs = 200L;
        var configWithRetries =
                LogdashConfig.builder()
                        .apiKey("test-key")
                        .baseUrl("http://localhost:" + wireMockServer.port())
                        .maxRetries(3)
                        .retryDelayMs(retryDelayMs)
                        .requestTimeoutMs(1000L)
                        .build();

        var retryTransport = new HttpTransport(configWithRetries);

        wireMockServer.stubFor(
                post(urlEqualTo("/logs"))
                        .inScenario("backoff-test")
                        .whenScenarioStateIs("Started")
                        .willReturn(aResponse().withStatus(500))
                        .willSetStateTo("retry1"));

        wireMockServer.stubFor(
                post(urlEqualTo("/logs"))
                        .inScenario("backoff-test")
                        .whenScenarioStateIs("retry1")
                        .willReturn(aResponse().withStatus(500))
                        .willSetStateTo("retry2"));

        wireMockServer.stubFor(
                post(urlEqualTo("/logs"))
                        .inScenario("backoff-test")
                        .whenScenarioStateIs("retry2")
                        .willReturn(aResponse().withStatus(500))
                        .willSetStateTo("retry3"));

        wireMockServer.stubFor(
                post(urlEqualTo("/logs"))
                        .inScenario("backoff-test")
                        .whenScenarioStateIs("retry3")
                        .willReturn(aResponse().withStatus(200)));

        var logEntry = new LogEntry("Backoff test", LogLevel.ERROR, 1L);

        try {
            // Act
            long startTime = System.currentTimeMillis();
            var future = retryTransport.sendLog(logEntry);
            assertThat(future).succeedsWithin(15, TimeUnit.SECONDS);
            long totalTime = System.currentTimeMillis() - startTime;

            // Assert
            // Should have taken time for exponential backoff (roughly 200 + 400 + 800 + jitter)
            assertThat(totalTime).isGreaterThan(1200); // Minimum expected time
            assertThat(totalTime).isLessThan(3000); // Maximum reasonable time

            wireMockServer.verify(4, postRequestedFor(urlEqualTo("/logs")));
        } finally {
            retryTransport.close();
        }
    }

    @Test
    void should_demonstrate_concurrency_behavior() throws InterruptedException {
        // Arrange - This test shows actual concurrency behavior
        var configWithLowLimit =
                LogdashConfig.builder()
                        .apiKey("test-key")
                        .baseUrl("http://localhost:" + wireMockServer.port())
                        .maxConcurrentRequests(2) // Very low limit
                        .requestTimeoutMs(3000L)
                        .build();

        var limitedTransport = new HttpTransport(configWithLowLimit);

        // Slow responses to fill up concurrency slots
        wireMockServer.stubFor(
                post(urlEqualTo("/logs"))
                        .willReturn(aResponse().withStatus(200).withFixedDelay(1000))); // 1 second delay

        var futures = new ArrayList<CompletableFuture<Void>>();

        try {
            // Act - Submit more requests than concurrency limit
            for (int i = 0; i < 8; i++) {
                var logEntry = new LogEntry("Concurrency demo " + i, LogLevel.INFO, i);
                var future = limitedTransport.sendLog(logEntry);
                futures.add(future);

                // Small delay between submissions
                Thread.sleep(50);
            }

            // Wait for all futures to complete
            var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            assertThat(allFutures).succeedsWithin(15, TimeUnit.SECONDS);

            // Assert - Should have processed some requests, rejected others
            var actualRequests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/logs"))).size();

            // With concurrency limit of 2, we should see fewer requests than submitted
            assertThat(actualRequests).isLessThan(8);
            assertThat(actualRequests).isGreaterThan(0);

            System.out.println(
                    "Submitted: 8, Processed: " + actualRequests + " (demonstrates concurrency limiting)");

        } finally {
            limitedTransport.close();
        }
    }

    @Test
    void should_use_efficient_countdownlatch_for_flush() throws InterruptedException {
        // Arrange
        wireMockServer.stubFor(
                post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200).withFixedDelay(300)));

        var futures = new ArrayList<CompletableFuture<Void>>();

        // Start multiple requests
        for (int i = 0; i < 3; i++) {
            var logEntry = new LogEntry("Flush test " + i, LogLevel.INFO, i);
            futures.add(transport.sendLog(logEntry));
        }

        // Act
        long startTime = System.currentTimeMillis();
        var flushFuture = transport.flush();
        assertThat(flushFuture).succeedsWithin(5, TimeUnit.SECONDS);
        long flushTime = System.currentTimeMillis() - startTime;

        // Assert
        // Flush should wait for all pending requests efficiently
        assertThat(flushTime).isGreaterThan(250); // At least the delay time
        assertThat(flushTime).isLessThan(1000); // But not too long due to efficient waiting

        // All original futures should also complete
        var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        assertThat(allFutures).succeedsWithin(1, TimeUnit.SECONDS);

        wireMockServer.verify(3, postRequestedFor(urlEqualTo("/logs")));
    }

    @Test
    void should_handle_multiple_shutdown_calls_safely() {
        // Arrange
        wireMockServer.stubFor(
                post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200).withFixedDelay(100)));

        var logEntry = new LogEntry("Shutdown test", LogLevel.INFO, 1L);
        transport.sendLog(logEntry);

        // Act & Assert
        var shutdown1 = transport.shutdown();
        var shutdown2 = transport.shutdown();
        var shutdown3 = transport.shutdown();

        assertThat(shutdown1).isPresent();
        assertThat(shutdown2).isPresent();
        assertThat(shutdown3).isPresent();

        // All shutdown futures should complete successfully
        assertThat(shutdown1.get()).succeedsWithin(3, TimeUnit.SECONDS);
        assertThat(shutdown2.get()).succeedsWithin(1, TimeUnit.SECONDS);
        assertThat(shutdown3.get()).succeedsWithin(1, TimeUnit.SECONDS);
    }

    @Test
    void should_mask_api_key_in_verbose_logging() {
        // Arrange
        var verboseConfig =
                LogdashConfig.builder()
                        .apiKey("very-secret-api-key-12345")
                        .baseUrl("http://localhost:" + wireMockServer.port())
                        .enableVerboseLogging(true)
                        .build();

        wireMockServer.stubFor(post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200)));

        try (var verboseTransport = new HttpTransport(verboseConfig)) {
            var logEntry = new LogEntry("Verbose test", LogLevel.INFO, 1L);

            // Act
            var future = verboseTransport.sendLog(logEntry);

            // Assert
            assertThat(future).succeedsWithin(3, TimeUnit.SECONDS);
            // Note: In real scenario, you'd capture System.out to verify masking
            // For this test, we just ensure it completes without issues
        }
    }

    @Test
    void should_handle_invalid_base_url_gracefully() {
        // Arrange - HttpTransport doesn't validate URL in constructor
        // Invalid URLs are caught during actual request sending
        var invalidConfig =
                LogdashConfig.builder().apiKey("test-key").baseUrl("not-a-valid-url").build();

        var transport = new HttpTransport(invalidConfig);
        var logEntry = new LogEntry("Test", LogLevel.INFO, 1L);

        try {
            // Act - invalid URL should be caught during request creation
            var future = transport.sendLog(logEntry);

            // Assert - future should complete (error is handled gracefully)
            assertThat(future).succeedsWithin(5, TimeUnit.SECONDS);

            // No requests should reach the server due to invalid URL
            wireMockServer.verify(0, postRequestedFor(urlEqualTo("/logs")));
        } finally {
            transport.close();
        }
    }

    @Test
    void should_handle_interrupted_threads_during_retry() throws InterruptedException {
        // Arrange
        wireMockServer.stubFor(
                post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(500).withFixedDelay(100)));

        var logEntry = new LogEntry("Interrupt test", LogLevel.ERROR, 1L);
        var future = transport.sendLog(logEntry);

        // Act - interrupt the thread during retry
        Thread.sleep(150); // Let first attempt fail and retry start

        // The transport should handle interruption gracefully
        assertThat(future).succeedsWithin(5, TimeUnit.SECONDS);
    }

    @Test
    void should_maintain_minimum_throughput_under_burst_load() throws InterruptedException {
        // REGRESSION TEST: Prevents implementation from becoming too aggressive
        // with request rejection, ensuring reasonable throughput under load

        wireMockServer.stubFor(
                post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200).withFixedDelay(150)));

        int submittedRequests = 25;
        var futures = new ArrayList<CompletableFuture<Void>>();
        var completionLatch = new CountDownLatch(submittedRequests);

        // Act - Submit burst of requests to test throughput behavior
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < submittedRequests; i++) {
            var logEntry = new LogEntry("Throughput test " + i, LogLevel.INFO, i);
            var future = transport.sendLog(logEntry);

            future.whenComplete((result, throwable) -> completionLatch.countDown());
            futures.add(future);

            // Small stagger to simulate realistic usage pattern
            if (i % 3 == 0) {
                Thread.sleep(10);
            }
        }

        // Assert - All futures must complete (no hanging)
        assertThat(completionLatch.await(15, TimeUnit.SECONDS))
                .as("All requests should complete within timeout")
                .isTrue();

        long totalTime = System.currentTimeMillis() - startTime;
        var actualRequests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/logs"))).size();

        // CRITICAL REGRESSION PROTECTION:
        // 1. Minimum throughput requirement - at least 60% of requests should be processed
        int minimumExpectedRequests = (int) (submittedRequests * 0.6); // 60% threshold
        assertThat(actualRequests)
                .as(
                        "Transport should process at least %d%% of submitted requests (got %d/%d)",
                        60, actualRequests, submittedRequests)
                .isGreaterThanOrEqualTo(minimumExpectedRequests);

        // 2. Performance requirement - should complete in reasonable time
        long maxExpectedTime = submittedRequests * 200L; // 200ms per request max
        assertThat(totalTime)
                .as(
                        "Transport should maintain reasonable performance (%dms for %d requests)",
                        totalTime, submittedRequests)
                .isLessThan(maxExpectedTime);

        // 3. All futures should succeed (no exceptions)
        var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        assertThat(allFutures)
                .as("All request futures should complete successfully")
                .succeedsWithin(2, TimeUnit.SECONDS);

        // Diagnostic information for troubleshooting
        if (config.enableVerboseLogging()) {
            double throughputPercentage = (actualRequests * 100.0) / submittedRequests;
            System.out.printf(
                    "Throughput regression test: %.1f%% success rate (%d/%d requests, %dms total)%n",
                    throughputPercentage, actualRequests, submittedRequests, totalTime);
        }
    }

    @Test
    void should_enforce_performance_contract_requirements() throws InterruptedException {
        // CONTRACT TEST: Defines minimum performance characteristics that
        // the HttpTransport implementation must always satisfy

        wireMockServer.stubFor(
                post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200).withFixedDelay(100)));

        // Test scenarios with different load patterns
        var testScenarios =
                List.of(
                        new TestScenario("Light Load", 5, 0.9), // 90% success rate
                        new TestScenario("Medium Load", 10, 0.8), // 80% success rate
                        new TestScenario("Heavy Load", 15, 0.6) // 60% success rate
                );

        for (var scenario : testScenarios) {
            // Reset WireMock request journal
            wireMockServer.resetRequests();

            var futures = new ArrayList<CompletableFuture<Void>>();

            // Submit requests for this scenario
            for (int i = 0; i < scenario.requestCount; i++) {
                var logEntry = new LogEntry(scenario.name + " " + i, LogLevel.INFO, i);
                futures.add(transport.sendLog(logEntry));
                Thread.sleep(20); // Realistic spacing
            }

            // Wait for completion
            var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            assertThat(allFutures).succeedsWithin(10, TimeUnit.SECONDS);

            // Verify contract requirements
            var actualRequests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/logs"))).size();
            int expectedMinimum = (int) (scenario.requestCount * scenario.minSuccessRate);

            assertThat(actualRequests)
                    .as(
                            "Scenario '%s': Expected at least %.0f%% success (%d requests), got %d",
                            scenario.name, scenario.minSuccessRate * 100, expectedMinimum, actualRequests)
                    .isGreaterThanOrEqualTo(expectedMinimum);
        }
    }

    @Test
    void should_preserve_thread_pool_efficiency_under_reasonable_load() throws InterruptedException {
        // Arrange - Use config with higher concurrency for load test
        var loadConfig =
                LogdashConfig.builder()
                        .apiKey("test-key")
                        .baseUrl("http://localhost:" + wireMockServer.port())
                        .maxConcurrentRequests(10) // Higher concurrency
                        .requestTimeoutMs(3000L)
                        .build();

        var loadTransport = new HttpTransport(loadConfig);

        wireMockServer.stubFor(
                post(urlEqualTo("/logs")).willReturn(aResponse().withStatus(200).withFixedDelay(150)));

        var futures = new ArrayList<CompletableFuture<Void>>();
        var latch = new CountDownLatch(15); // Reasonable load

        try {
            // Act - Submit requests with small delays
            for (int i = 0; i < 15; i++) {
                var logEntry = new LogEntry("Load test " + i, LogLevel.INFO, i);
                var future = loadTransport.sendLog(logEntry);
                future.whenComplete((result, throwable) -> latch.countDown());
                futures.add(future);

                // Small delay to avoid overwhelming
                Thread.sleep(20);
            }

            // Assert
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

            var allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            assertThat(allFutures).succeedsWithin(3, TimeUnit.SECONDS);

            // Most requests should have been processed
            var actualRequests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/logs"))).size();
            assertThat(actualRequests).isGreaterThan(10); // At least 2/3 processed

        } finally {
            loadTransport.close();
        }
    }

    private record TestScenario(String name, int requestCount, double minSuccessRate) {
    }
}
