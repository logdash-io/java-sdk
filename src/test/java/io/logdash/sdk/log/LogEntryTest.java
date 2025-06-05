package io.logdash.sdk.log;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogEntryTest {

    @Test
    void should_create_log_entry_with_all_parameters() {
        // Arrange
        var timestamp = Instant.now();
        Map<String, Object> context = Map.of("key", "value");

        // Act
        var logEntry = new LogEntry("Test message", LogLevel.INFO, timestamp, 42L, context);

        // Assert
        assertThat(logEntry.message()).isEqualTo("Test message");
        assertThat(logEntry.level()).isEqualTo(LogLevel.INFO);
        assertThat(logEntry.createdAt()).isEqualTo(timestamp);
        assertThat(logEntry.sequenceNumber()).isEqualTo(42L);
        assertThat(logEntry.context()).containsEntry("key", "value");
    }

    @Test
    void should_create_log_entry_with_minimal_parameters() {
        // Act
        var logEntry = new LogEntry("Test message", LogLevel.ERROR, 123L);

        // Assert
        assertThat(logEntry.message()).isEqualTo("Test message");
        assertThat(logEntry.level()).isEqualTo(LogLevel.ERROR);
        assertThat(logEntry.sequenceNumber()).isEqualTo(123L);
        assertThat(logEntry.createdAt()).isNotNull();
        assertThat(logEntry.context()).isEmpty();
    }

    @Test
    void should_create_log_entry_with_context() {
        // Arrange
        Map<String, Object> context = Map.of("userId", "123", "action", "login");

        // Act
        var logEntry = new LogEntry("User action", LogLevel.INFO, 1L, context);

        // Assert
        assertThat(logEntry.message()).isEqualTo("User action");
        assertThat(logEntry.level()).isEqualTo(LogLevel.INFO);
        assertThat(logEntry.sequenceNumber()).isEqualTo(1L);
        assertThat(logEntry.context()).containsEntry("userId", "123");
        assertThat(logEntry.context()).containsEntry("action", "login");
    }

    @Test
    void should_throw_exception_for_null_message() {
        assertThatThrownBy(() ->
                new LogEntry(null, LogLevel.INFO, 1L)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message cannot be null");
    }

    @Test
    void should_throw_exception_for_null_level() {
        assertThatThrownBy(() ->
                new LogEntry("Test", null, 1L)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Level cannot be null");
    }

    @Test
    void should_handle_null_timestamp_by_using_current_time() {
        // Act
        var logEntry = new LogEntry("Test", LogLevel.INFO, null, 1L, Map.of());

        // Assert
        assertThat(logEntry.createdAt()).isNotNull();
        assertThat(logEntry.createdAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void should_handle_null_context_by_using_empty_map() {
        // Act
        var logEntry = new LogEntry("Test", LogLevel.INFO, Instant.now(), 1L, null);

        // Assert
        assertThat(logEntry.context()).isNotNull();
        assertThat(logEntry.context()).isEmpty();
    }

    @Test
    void should_create_defensive_copy_of_context() {
        // Arrange
        Map<String, Object> originalContext = new HashMap<>();
        originalContext.put("key", "value");

        // Act
        var logEntry = new LogEntry("Test", LogLevel.INFO, 1L, originalContext);

        // Modify original map
        originalContext.put("newKey", "newValue");

        // Assert
        assertThat(logEntry.context()).containsOnlyKeys("key");
        assertThat(logEntry.context()).doesNotContainKey("newKey");
        assertThat(logEntry.context()).hasSize(1);
    }

    @Test
    void should_handle_empty_context_map() {
        // Act
        var logEntry = new LogEntry("Test", LogLevel.INFO, 1L, Map.of());

        // Assert
        assertThat(logEntry.context()).isEmpty();
        assertThat(logEntry.context()).isNotNull();
    }

    @Test
    void should_handle_context_with_null_values() {
        // Arrange
        Map<String, Object> contextWithNull = new HashMap<>();
        contextWithNull.put("validKey", "validValue");
        contextWithNull.put("nullKey", null);

        // Act
        var logEntry = new LogEntry("Test", LogLevel.INFO, 1L, contextWithNull);

        // Assert
        assertThat(logEntry.context()).hasSize(2);
        assertThat(logEntry.context()).containsEntry("validKey", "validValue");
        assertThat(logEntry.context()).containsEntry("nullKey", null);
    }

    @Test
    void should_handle_different_context_value_types() {
        // Arrange
        Map<String, Object> context = new HashMap<>();
        context.put("stringValue", "test");
        context.put("intValue", 42);
        context.put("doubleValue", 3.14);
        context.put("booleanValue", true);

        // Act
        var logEntry = new LogEntry("Test", LogLevel.INFO, 1L, context);

        // Assert
        assertThat(logEntry.context()).hasSize(4);
        assertThat(logEntry.context().get("stringValue")).isEqualTo("test");
        assertThat(logEntry.context().get("intValue")).isEqualTo(42);
        assertThat(logEntry.context().get("doubleValue")).isEqualTo(3.14);
        assertThat(logEntry.context().get("booleanValue")).isEqualTo(true);
    }
}
