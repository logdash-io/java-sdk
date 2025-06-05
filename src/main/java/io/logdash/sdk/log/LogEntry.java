package io.logdash.sdk.log;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable data container representing a log entry to be sent to Logdash.
 *
 * <p>A log entry contains:
 *
 * <ul>
 *   <li>The log message text
 *   <li>Severity level indicating the importance
 *   <li>Timestamp when the log was created
 *   <li>Sequence number for maintaining order
 *   <li>Optional context data for structured logging
 * </ul>
 *
 * @param message        the log message text, must not be null
 * @param level          the severity level, must not be null
 * @param createdAt      the timestamp when this log was created
 * @param sequenceNumber monotonically increasing number for ordering
 * @param context        additional structured data as key-value pairs
 */
public record LogEntry(
        String message,
        LogLevel level,
        Instant createdAt,
        long sequenceNumber,
        Map<String, Object> context) {
    private static final int MAX_MESSAGE_LENGTH = 100_000;
    private static final int MAX_CONTEXT_ENTRIES = 100;

    /**
     * Validates and normalizes the log entry parameters during construction.
     *
     * @throws IllegalArgumentException if message or level is null
     */
    public LogEntry {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            message =
                    message.substring(0, MAX_MESSAGE_LENGTH - 50)
                            + " ... [TRUNCATED: original length was "
                            + message.length()
                            + " chars]";
        }
        if (level == null) {
            throw new IllegalArgumentException("Level cannot be null");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (context == null) {
            context = Map.of();
        } else {
            context = validateAndLimitContext(context);
        }
    }

    /**
     * Creates a log entry with current timestamp and no context.
     *
     * @param message        the log message
     * @param level          the log level
     * @param sequenceNumber the sequence number for ordering
     */
    public LogEntry(String message, LogLevel level, long sequenceNumber) {
        this(message, level, Instant.now(), sequenceNumber, Map.of());
    }

    /**
     * Creates a log entry with current timestamp and context data.
     *
     * @param message        the log message
     * @param level          the log level
     * @param sequenceNumber the sequence number for ordering
     * @param context        additional context data
     */
    public LogEntry(
            String message, LogLevel level, long sequenceNumber, Map<String, Object> context) {
        this(message, level, Instant.now(), sequenceNumber, context);
    }

    private static Map<String, Object> validateAndLimitContext(Map<String, Object> context) {
        if (context.size() > MAX_CONTEXT_ENTRIES) {
            var limitedContext = new LinkedHashMap<String, Object>();
            var iterator = context.entrySet().iterator();
            int count = 0;

            while (iterator.hasNext() && count < MAX_CONTEXT_ENTRIES - 1) {
                var entry = iterator.next();
                limitedContext.put(entry.getKey(), entry.getValue());
                count++;
            }

            limitedContext.put(
                    "_truncated",
                    "Context limited to " + MAX_CONTEXT_ENTRIES + " entries from original " + context.size());

            return Collections.unmodifiableMap(limitedContext);
        } else {
            return Collections.unmodifiableMap(new HashMap<>(context));
        }
    }

    /**
     * Returns the context data for this log entry.
     *
     * @return an immutable copy of the context map
     */
    public Map<String, Object> context() {
        return Collections.unmodifiableMap(new HashMap<>(context));
    }
}
