package io.logdash.sdk.util;

import io.logdash.sdk.log.LogEntry;
import io.logdash.sdk.log.LogLevel;
import io.logdash.sdk.metrics.MetricEntry;
import io.logdash.sdk.metrics.MetricType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSerializerEdgeCasesTest {

    private JsonSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new JsonSerializer();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "Simple message",
                    "Message with émojis 🚀🎉",
                    "Message\nwith\nnewlines",
                    "Message with \"quotes\"",
                    "Message with 中文字符",
                    ""
            })
    void should_handle_various_message_formats(String message) {
        // Arrange
        var logEntry = new LogEntry(message, LogLevel.INFO, 1L);

        // Act
        var json = serializer.serialize(logEntry);

        // Assert
        assertThat(json).contains("\"message\":");
        assertThat(json).contains("\"level\":\"info\"");
        assertThat(json).isNotNull();
    }

    @Test
    void should_handle_extremely_large_numbers() {
        // Arrange
        var maxLong = new MetricEntry("max_long", Long.MAX_VALUE, MetricType.SET);
        var minLong = new MetricEntry("min_long", Long.MIN_VALUE, MetricType.SET);
        var maxDouble = new MetricEntry("max_double", Double.MAX_VALUE, MetricType.SET);
        var minDouble = new MetricEntry("min_double", -Double.MAX_VALUE, MetricType.SET);

        // Act & Assert
        assertThat(serializer.serialize(maxLong)).contains(String.valueOf(Long.MAX_VALUE));
        assertThat(serializer.serialize(minLong)).contains(String.valueOf(Long.MIN_VALUE));
        assertThat(serializer.serialize(maxDouble)).contains("value");
        assertThat(serializer.serialize(minDouble)).contains("value");
    }

    @Test
    void should_handle_concurrent_serialization() throws InterruptedException {
        // Arrange
        var executor = java.util.concurrent.Executors.newFixedThreadPool(10);
        var latch = new java.util.concurrent.CountDownLatch(100);
        var results = new java.util.concurrent.ConcurrentLinkedQueue<String>();

        // Act
        for (int i = 0; i < 100; i++) {
            final int iteration = i;
            executor.submit(
                    () -> {
                        try {
                            var logEntry = new LogEntry("Message " + iteration, LogLevel.INFO, iteration);
                            var json = serializer.serialize(logEntry);
                            results.add(json);
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        // Assert
        assertThat(latch.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(results).hasSize(100);
        assertThat(results)
                .allSatisfy(
                        json -> {
                            assertThat(json).contains("\"message\":");
                            assertThat(json).contains("\"level\":\"info\"");
                        });

        executor.shutdown();
    }

    @Test
    void should_preserve_timestamp_precision() {
        // Arrange
        var timestamp = Instant.parse("2024-12-25T10:15:30.123456789Z");
        var logEntry = new LogEntry("Precision test", LogLevel.INFO, timestamp, 1L, Map.of());

        // Act
        var json = serializer.serialize(logEntry);

        // Assert
        assertThat(json).contains("\"createdAt\":\"2024-12-25T10:15:30.123456789Z\"");
    }
}
