package io.logdash.sdk.log;

/**
 * Defines the severity levels for log messages in Logdash.
 *
 * <p>Log levels are ordered from most severe ({@link #ERROR}) to least severe ({@link #SILLY}).
 * Each level corresponds to a specific string value used in the Logdash API.
 */
public enum LogLevel {
    /**
     * Error level for application errors and exceptions
     */
    ERROR("error"),

    /**
     * Warning level for potentially harmful situations
     */
    WARN("warning"),

    /**
     * Informational level for general application flow
     */
    INFO("info"),

    /**
     * HTTP level for HTTP request/response logging
     */
    HTTP("http"),

    /**
     * Verbose level for detailed operational information
     */
    VERBOSE("verbose"),

    /**
     * Debug level for detailed diagnostic information
     */
    DEBUG("debug"),

    /**
     * Silly level for very detailed trace information
     */
    SILLY("silly");

    private final String value;

    LogLevel(String value) {
        this.value = value;
    }

    /**
     * Returns the string value used in the Logdash API.
     *
     * @return the API string representation of this log level
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
