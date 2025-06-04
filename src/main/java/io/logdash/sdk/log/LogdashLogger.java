package io.logdash.sdk.log;

import io.logdash.sdk.config.LogdashConfig;
import io.logdash.sdk.transport.LogdashTransport;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Primary interface for sending structured log messages to Logdash platform.
 *
 * <p>Provides convenient methods for logging at different severity levels with optional structured
 * context data. All log entries are sent asynchronously and are thread-safe for high-throughput
 * scenarios.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * logger.info("User logged in successfully");
 * logger.error("Database connection failed", Map.of("userId", 123, "retries", 3));
 * logger.debug("Processing request", Map.of("requestId", "req-456"));
 * }</pre>
 */
public final class LogdashLogger {

    private static final DateTimeFormatter CONSOLE_FORMATTER = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String GREEN = "\u001B[32m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";

    private final LogdashConfig config;
    private final LogdashTransport transport;
    private final AtomicLong sequenceNumber = new AtomicLong(0);

    /**
     * Creates a new logger instance with the specified configuration and transport.
     *
     * <p><strong>Note:</strong> This constructor is internal to the SDK. Use {@link
     * io.logdash.sdk.Logdash#logger()} to obtain an instance.
     *
     * @param config    the SDK configuration
     * @param transport the transport for sending log entries
     */
    public LogdashLogger(LogdashConfig config, LogdashTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    /**
     * Logs an error message indicating a serious problem.
     *
     * @param message the error message
     */
    public void error(String message) {
        log(LogLevel.ERROR, message, Map.of());
    }

    /**
     * Logs an error message with additional structured context.
     *
     * @param message the error message
     * @param context additional context data as key-value pairs
     */
    public void error(String message, Map<String, Object> context) {
        log(LogLevel.ERROR, message, context);
    }

    /**
     * Logs a warning message indicating a potentially harmful situation.
     *
     * @param message the warning message
     */
    public void warn(String message) {
        log(LogLevel.WARN, message, Map.of());
    }

    /**
     * Logs a warning message with additional structured context.
     *
     * @param message the warning message
     * @param context additional context data as key-value pairs
     */
    public void warn(String message, Map<String, Object> context) {
        log(LogLevel.WARN, message, context);
    }

    /**
     * Logs an informational message about normal application flow.
     *
     * @param message the informational message
     */
    public void info(String message) {
        log(LogLevel.INFO, message, Map.of());
    }

    /**
     * Logs an informational message with additional structured context.
     *
     * @param message the informational message
     * @param context additional context data as key-value pairs
     */
    public void info(String message, Map<String, Object> context) {
        log(LogLevel.INFO, message, context);
    }

    /**
     * Logs an HTTP-related message (requests, responses, etc.).
     *
     * @param message the HTTP-related message
     */
    public void http(String message) {
        log(LogLevel.HTTP, message, Map.of());
    }

    /**
     * Logs an HTTP-related message with additional structured context.
     *
     * @param message the HTTP-related message
     * @param context additional context data as key-value pairs
     */
    public void http(String message, Map<String, Object> context) {
        log(LogLevel.HTTP, message, context);
    }

    /**
     * Logs a verbose message with detailed operational information.
     *
     * @param message the verbose message
     */
    public void verbose(String message) {
        log(LogLevel.VERBOSE, message, Map.of());
    }

    /**
     * Logs a verbose message with additional structured context.
     *
     * @param message the verbose message
     * @param context additional context data as key-value pairs
     */
    public void verbose(String message, Map<String, Object> context) {
        log(LogLevel.VERBOSE, message, context);
    }

    /**
     * Logs a debug message with detailed diagnostic information.
     *
     * @param message the debug message
     */
    public void debug(String message) {
        log(LogLevel.DEBUG, message, Map.of());
    }

    /**
     * Logs a debug message with additional structured context.
     *
     * @param message the debug message
     * @param context additional context data as key-value pairs
     */
    public void debug(String message, Map<String, Object> context) {
        log(LogLevel.DEBUG, message, context);
    }

    /**
     * Logs a silly/trace level message with very detailed information.
     *
     * @param message the trace-level message
     */
    public void silly(String message) {
        log(LogLevel.SILLY, message, Map.of());
    }

    /**
     * Logs a silly/trace level message with additional structured context.
     *
     * @param message the trace-level message
     * @param context additional context data as key-value pairs
     */
    public void silly(String message, Map<String, Object> context) {
        log(LogLevel.SILLY, message, context);
    }

    private long getNextSequenceNumber() {
        long current, next;
        do {
            current = sequenceNumber.get();
            next = current == Long.MAX_VALUE ? 1 : current + 1;
        } while (!sequenceNumber.compareAndSet(current, next));
        return next;
    }

    private void log(LogLevel level, String message, Map<String, Object> context) {
        var finalMessage = buildFinalMessage(message, context);
        var logEntry = new LogEntry(finalMessage, level, getNextSequenceNumber());

        if (config.enableConsoleOutput()) {
            printToConsole(message, level, context);
        }

        transport.sendLog(logEntry);
    }

    private String buildFinalMessage(String message, Map<String, Object> context) {
        if (context.isEmpty()) {
            return message;
        }

        var contextJson = buildContextJson(context);
        return message + " " + contextJson;
    }

    private String buildContextJson(Map<String, Object> context) {
        if (context.isEmpty()) {
            return "{}";
        }

        var sb = new StringBuilder(context.size() * 32);
        sb.append("{");

        var iterator = context.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            sb.append("\"").append(escapeJsonString(entry.getKey())).append("\":");
            sb.append(formatJsonValue(entry.getValue()));

            if (iterator.hasNext()) {
                sb.append(",");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    private String formatJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String str) {
            return "\"" + escapeJsonString(str) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return "\"" + escapeJsonString(String.valueOf(value)) + "\"";
    }

    private String escapeJsonString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void printToConsole(String originalMessage, LogLevel level, Map<String, Object> context) {
        var timestamp = OffsetDateTime.now().format(CONSOLE_FORMATTER);
        var levelColor = getLevelColor(level);
        var levelStr = level.getValue();

        var contextStr = context.isEmpty() ? "" : " " + formatContext(context);

        System.out.printf(
                "%s [%s%s%s] %s%s%n", timestamp, levelColor, levelStr, RESET, originalMessage, contextStr);
    }

    private String formatContext(Map<String, Object> context) {
        if (context.isEmpty()) {
            return "";
        }

        var sb = new StringBuilder("{");
        var first = true;
        for (var entry : context.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String getLevelColor(LogLevel level) {
        return switch (level) {
            case ERROR -> RED;
            case WARN -> YELLOW;
            case INFO -> BLUE;
            case HTTP -> GREEN;
            case VERBOSE -> PURPLE;
            case DEBUG -> CYAN;
            case SILLY -> GRAY;
        };
    }
}
