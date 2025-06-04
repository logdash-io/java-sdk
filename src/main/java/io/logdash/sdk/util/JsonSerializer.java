package io.logdash.sdk.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.logdash.sdk.log.LogEntry;
import io.logdash.sdk.metrics.MetricEntry;

import java.util.Map;

/**
 * Internal JSON serializer for converting SDK objects to JSON format.
 *
 * <p>This lightweight, dependency-free serializer is specifically designed for Logdash SDK objects.
 * It handles proper escaping and formatting according to JSON specification.
 *
 * <p><strong>Note:</strong> This class is internal to the SDK and should not be used directly by
 * client applications.
 */
public final class JsonSerializer {
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
    }

    /**
     * Serializes a log entry to JSON format.
     *
     * @param logEntry the log entry to serialize, must not be null
     * @return JSON string representation
     */
    public String serialize(LogEntry logEntry) {
        try {
            var logData =
                    Map.of(
                            "message", sanitizeString(logEntry.message()),
                            "level", logEntry.level().getValue(),
                            "createdAt", logEntry.createdAt(),
                            "sequenceNumber", logEntry.sequenceNumber());
            return OBJECT_MAPPER.writeValueAsString(logData);
        } catch (JsonProcessingException e) {
            return createFallbackLogJson(logEntry, e);
        }
    }

    /**
     * Serializes a metric entry to JSON format.
     *
     * @param metricEntry the metric entry to serialize, must not be null
     * @return JSON string representation
     */
    public String serialize(MetricEntry metricEntry) {
        try {
            var metricData =
                    Map.of(
                            "name", sanitizeString(metricEntry.name()),
                            "value", sanitizeNumber(metricEntry.value()),
                            "operation", metricEntry.operation().getValue());
            return OBJECT_MAPPER.writeValueAsString(metricData);
        } catch (JsonProcessingException e) {
            return createFallbackMetricJson(metricEntry, e);
        }
    }

    private String sanitizeString(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\p{Cntrl}&&[^\t\n\r]]", "");
    }

    private Number sanitizeNumber(Number number) {
        if (number instanceof Double d) {
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return 0.0;
            }
        }
        if (number instanceof Float f) {
            if (Float.isNaN(f) || Float.isInfinite(f)) {
                return 0.0f;
            }
        }
        return number;
    }

    private String createFallbackLogJson(LogEntry logEntry, Exception error) {
        return String.format(
                "{\"message\":\"%s\",\"level\":\"%s\",\"createdAt\":\"%s\",\"sequenceNumber\":%d,"
                        + "\"_error\":\"Serialization failed: %s\"}",
                escapeJsonString(logEntry.message()),
                logEntry.level().getValue(),
                logEntry.createdAt(),
                logEntry.sequenceNumber(),
                escapeJsonString(error.getMessage()));
    }

    private String createFallbackMetricJson(MetricEntry metricEntry, Exception error) {
        return String.format(
                "{\"name\":\"%s\",\"value\":0,\"operation\":\"%s\",\"_error\":\"Serialization failed: %s\"}",
                escapeJsonString(metricEntry.name()),
                metricEntry.operation().getValue(),
                escapeJsonString(error.getMessage()));
    }

    private String escapeJsonString(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
