package io.logdash.sdk.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.logdash.sdk.log.LogEntry;
import io.logdash.sdk.log.LogLevel;
import io.logdash.sdk.metrics.MetricEntry;
import io.logdash.sdk.metrics.MetricType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSerializerTest {

    private JsonSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new JsonSerializer();
    }

    @Test
    void should_serialize_simple_log_entry() {
        // Arrange
        var timestamp = Instant.parse("2024-06-01T12:34:56Z");
        var logEntry = new LogEntry("Test message", LogLevel.INFO, timestamp, 42L, Map.of());

        // Act
        var json = serializer.serialize(logEntry);

        // Assert
        assertThat(json).contains("\"message\":\"Test message\"");
        assertThat(json).contains("\"level\":\"info\"");
        assertThat(json).contains("\"createdAt\":\"2024-06-01T12:34:56Z\"");
        assertThat(json).contains("\"sequenceNumber\":42");
    }

    @Test
    void should_serialize_log_entry_with_special_characters() {
        // Arrange
        var logEntry = new LogEntry("Message with \"quotes\" and \n newlines", LogLevel.ERROR, 1L);

        // Act
        var json = serializer.serialize(logEntry);

        // Assert
        assertThat(json).contains("\\\"quotes\\\"");
        assertThat(json).contains("\\n");
    }

    @Test
    void should_serialize_metric_entry_with_integer_value() throws Exception {
        // Arrange
        var metricEntry = new MetricEntry("user_count", 100, MetricType.SET);

        // Act
        var json = serializer.serialize(metricEntry);

        // Assert
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);
        assertThat(node.get("name").asText()).isEqualTo("user_count");
        assertThat(node.get("value").asInt()).isEqualTo(100);
        assertThat(node.get("operation").asText()).isEqualTo("set");
    }

    @Test
    void should_serialize_metric_entry_with_double_value() {
        // Arrange
        var metricEntry = new MetricEntry("temperature", 23.5, MetricType.CHANGE);

        // Act
        var json = serializer.serialize(metricEntry);

        // Assert
        assertThat(json).contains("\"name\":\"temperature\"");
        assertThat(json).contains("\"value\":23.5");
        assertThat(json).contains("\"operation\":\"change\"");
    }

    @Test
    void should_handle_metric_with_special_characters_in_name() {
        // Arrange
        var metricEntry = new MetricEntry("metric/with\"special\\chars", 42, MetricType.SET);

        // Act
        var json = serializer.serialize(metricEntry);

        // Assert
        assertThat(json).contains("metric/with\\\"special\\\\chars");
    }

    @Test
    void should_handle_null_metric_name_gracefully() {
        assertThatThrownBy(() -> new MetricEntry(null, 42, MetricType.SET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Metric name cannot be null");
    }

    @Test
    void should_serialize_log_with_unicode_characters() {
        // Arrange
        var logEntry = new LogEntry("Message with émojis 🚀 and ñáéíóú", LogLevel.INFO, 1L);

        // Act
        var json = serializer.serialize(logEntry);

        // Assert
        assertThat(json).contains("émojis 🚀 and ñáéíóú");
    }

    @Test
    void should_handle_empty_and_single_character_strings() {
        // Arrange
        var emptyEntry = new LogEntry("", LogLevel.INFO, 1L);
        var singleCharEntry = new LogEntry("x", LogLevel.INFO, 2L);

        // Act
        var emptyJson = serializer.serialize(emptyEntry);
        var singleCharJson = serializer.serialize(singleCharEntry);

        // Assert
        assertThat(emptyJson).contains("\"message\":\"\"");
        assertThat(singleCharJson).contains("\"message\":\"x\"");
    }

    @Test
    void should_handle_all_control_characters_properly() {
        // Arrange
        var message = "Test\b\f\r\t\u0001\u001F";
        var logEntry = new LogEntry(message, LogLevel.INFO, 1L);

        // Act
        var json = serializer.serialize(logEntry);

        // Assert
        assertThat(json).contains("\\r");
        assertThat(json).contains("\\t");
        assertThat(json).doesNotContain("\\b");
        assertThat(json).doesNotContain("\\f");
        assertThat(json).doesNotContain("\\u0001");
        assertThat(json).doesNotContain("\\u001F");
    }

    @Test
    void should_handle_negative_numbers() {
        // Arrange
        var negativeInt = new MetricEntry("negative", -42, MetricType.SET);
        var negativeDouble = new MetricEntry("negative_double", -3.14, MetricType.CHANGE);

        // Act
        var intJson = serializer.serialize(negativeInt);
        var doubleJson = serializer.serialize(negativeDouble);

        // Assert
        assertThat(intJson).contains("-42");
        assertThat(doubleJson).contains("-3.14");
    }

    @Test
    void should_handle_zero_values() {
        // Arrange
        var zeroInt = new MetricEntry("zero", 0, MetricType.SET);
        var zeroDouble = new MetricEntry("zero_double", 0.0, MetricType.SET);

        // Act
        var intJson = serializer.serialize(zeroInt);
        var doubleJson = serializer.serialize(zeroDouble);

        // Assert
        assertThat(intJson).contains("\"value\":0");
        assertThat(doubleJson).contains("\"value\":0");
    }

    @Test
    void should_handle_very_long_strings() {
        // Arrange
        var longMessage = "A".repeat(10000);
        var logEntry = new LogEntry(longMessage, LogLevel.INFO, 1L);

        // Act
        var json = serializer.serialize(logEntry);

        // Assert
        assertThat(json).contains(longMessage);
        assertThat(json.length()).isGreaterThan(10000);
    }

    @Test
    void should_handle_nan_and_infinite_double_values() {
        // Arrange
        var nanMetric = new MetricEntry("nan_value", Double.NaN, MetricType.SET);
        var infiniteMetric =
                new MetricEntry("infinite_value", Double.POSITIVE_INFINITY, MetricType.SET);
        var negInfiniteMetric =
                new MetricEntry("neg_infinite", Double.NEGATIVE_INFINITY, MetricType.SET);

        // Act
        var nanJson = serializer.serialize(nanMetric);
        var infiniteJson = serializer.serialize(infiniteMetric);
        var negInfiniteJson = serializer.serialize(negInfiniteMetric);

        // Assert
        assertThat(nanJson).contains("\"value\":0.0");
        assertThat(infiniteJson).contains("\"value\":0.0");
        assertThat(negInfiniteJson).contains("\"value\":0.0");
    }

    @Test
    void should_format_large_integer_values_correctly() {
        // Arrange
        var largeInt = new MetricEntry("large_int", Long.MAX_VALUE, MetricType.SET);
        var largeDouble = new MetricEntry("large_double", 1.23456789012345e14, MetricType.SET);

        // Act
        var intJson = serializer.serialize(largeInt);
        var doubleJson = serializer.serialize(largeDouble);

        // Assert
        assertThat(intJson).contains(String.valueOf(Long.MAX_VALUE));
        // For large doubles, accept scientific notation as valid
        assertThat(doubleJson)
                .satisfiesAnyOf(
                        json -> assertThat(json).contains("123456789012345"),
                        json -> assertThat(json).contains("1.23456789012345E14"),
                        json -> assertThat(json).contains("1.23456789012345e14"));
    }

    @Test
    void should_handle_log_entries_with_timestamp_edge_cases() {
        // Arrange
        var epoch = Instant.EPOCH;
        var farFuture = Instant.parse("2099-12-31T23:59:59Z");

        var epochEntry = new LogEntry("Epoch", LogLevel.INFO, epoch, 1L, Map.of());
        var futureEntry = new LogEntry("Future", LogLevel.INFO, farFuture, 2L, Map.of());

        // Act
        var epochJson = serializer.serialize(epochEntry);
        var futureJson = serializer.serialize(futureEntry);

        // Assert
        assertThat(epochJson).contains("\"createdAt\":\"1970-01-01T00:00:00Z\"");
        assertThat(futureJson).contains("\"createdAt\":\"2099-12-31T23:59:59Z\"");
    }

    @Test
    void should_handle_different_number_types() {
        // Arrange
        var intMetric = new MetricEntry("int_metric", 42, MetricType.SET);
        var longMetric = new MetricEntry("long_metric", 123456789L, MetricType.SET);
        var floatMetric = new MetricEntry("float_metric", 3.14f, MetricType.SET);
        var doubleMetric = new MetricEntry("double_metric", 2.718281828, MetricType.SET);

        // Act & Assert
        assertThat(serializer.serialize(intMetric)).contains("\"value\":42");
        assertThat(serializer.serialize(longMetric)).contains("\"value\":123456789");
        assertThat(serializer.serialize(floatMetric)).contains("\"value\":3.14");
        assertThat(serializer.serialize(doubleMetric)).contains("\"value\":2.718281828");
    }

    @Test
    void should_escape_all_json_control_characters() {
        // Arrange
        var message = "Test\"\\\b\f\n\r\t\u0000\u001F";
        var logEntry = new LogEntry(message, LogLevel.INFO, 1L);

        // Act
        var json = serializer.serialize(logEntry);

        // Assert
        assertThat(json).contains("\\\""); // quote
        assertThat(json).contains("\\\\"); // backslash
        assertThat(json).contains("\\n"); // newline
        assertThat(json).contains("\\r"); // carriage return
        assertThat(json).contains("\\t"); // tab
        assertThat(json).doesNotContain("\\b"); // backspace
        assertThat(json).doesNotContain("\\f"); // form feed
        assertThat(json).doesNotContain("\\u0000"); // null character
        assertThat(json).doesNotContain("\\u001F"); // unit separator
    }

    @Test
    void should_handle_empty_strings_and_contexts() {
        // Arrange
        var emptyMessage = new LogEntry("", LogLevel.INFO, 1L);

        // Act
        var logJson = serializer.serialize(emptyMessage);

        // Assert
        assertThat(logJson).contains("\"message\":\"\"");

        // MetricEntry with empty name should throw
        assertThatThrownBy(() -> new MetricEntry("", 0, MetricType.SET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Metric name cannot be null or blank");
    }
}
