package io.logdash.sdk.config;

import io.logdash.sdk.exception.LogdashException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Configuration record for Logdash SDK client settings.
 *
 * <p>This record contains all configuration parameters needed to initialize and operate the Logdash
 * SDK, including API credentials, network settings, and behavioral options.
 *
 * @param apiKey                API key for authenticating with Logdash.io
 * @param baseUrl               Base URL for the Logdash API (defaults to https://api.logdash.io)
 * @param enableConsoleOutput   Whether to output logs and metrics to console
 * @param enableVerboseLogging  Whether to enable verbose internal logging
 * @param maxRetries            Maximum number of retry attempts for failed requests
 * @param retryDelayMs          Delay between retry attempts in milliseconds
 * @param requestTimeoutMs      HTTP request timeout in milliseconds
 * @param shutdownTimeoutMs     Maximum time to wait for graceful shutdown
 * @param maxConcurrentRequests Maximum number of concurrent HTTP requests
 */
public record LogdashConfig(
        String apiKey,
        String baseUrl,
        boolean enableConsoleOutput,
        boolean enableVerboseLogging,
        int maxRetries,
        long retryDelayMs,
        long requestTimeoutMs,
        long shutdownTimeoutMs,
        int maxConcurrentRequests) {
    private static final int MIN_API_KEY_LENGTH = 3;
    private static final int MAX_API_KEY_LENGTH = 256;

    public LogdashConfig {
        validateConfiguration(
                apiKey,
                baseUrl,
                maxRetries,
                retryDelayMs,
                requestTimeoutMs,
                shutdownTimeoutMs,
                maxConcurrentRequests);
    }

    /**
     * Creates a new builder for configuring LogdashConfig instances.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    private static void validateConfiguration(
            String apiKey,
            String baseUrl,
            int maxRetries,
            long retryDelayMs,
            long requestTimeoutMs,
            long shutdownTimeoutMs,
            int maxConcurrentRequests) {

        if (apiKey != null && !apiKey.isBlank()) {
            if (apiKey.length() < MIN_API_KEY_LENGTH || apiKey.length() > MAX_API_KEY_LENGTH) {
                throw new LogdashException(
                        String.format(
                                "API key length must be between %d and %d characters",
                                MIN_API_KEY_LENGTH, MAX_API_KEY_LENGTH));
            }
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new LogdashException("Base URL cannot be null or blank");
        }

        try {
            var uri = new URI(baseUrl);
            var scheme = uri.getScheme();
            if (scheme != null
                    && !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Unsupported URL scheme: " + scheme);
            }
        } catch (URISyntaxException e) {
            throw new LogdashException("Invalid base URL: " + baseUrl, e);
        } catch (IllegalArgumentException e) {
            throw new LogdashException("Invalid base URL: " + baseUrl, e);
        }

        if (maxRetries < 0) {
            throw new LogdashException("Max retries cannot be negative");
        }

        if (retryDelayMs < 0) {
            throw new LogdashException("Retry delay cannot be negative");
        }

        if (requestTimeoutMs <= 0) {
            throw new LogdashException("Request timeout must be positive");
        }

        if (shutdownTimeoutMs <= 0) {
            throw new LogdashException("Shutdown timeout must be positive");
        }

        if (maxConcurrentRequests <= 0) {
            throw new LogdashException("Max concurrent requests must be positive");
        }
    }

    public static final class Builder {
        private String apiKey = null;
        private String baseUrl = "https://api.logdash.io";
        private boolean enableConsoleOutput = true;
        private boolean enableVerboseLogging = false;
        private int maxRetries = 3;
        private long retryDelayMs = 500L;
        private long requestTimeoutMs = 15000L;
        private long shutdownTimeoutMs = 10000L;
        private int maxConcurrentRequests = 10;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder enableConsoleOutput(boolean enableConsoleOutput) {
            this.enableConsoleOutput = enableConsoleOutput;
            return this;
        }

        public Builder enableVerboseLogging(boolean enableVerboseLogging) {
            this.enableVerboseLogging = enableVerboseLogging;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder retryDelayMs(long retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
            return this;
        }

        public Builder requestTimeoutMs(long requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
            return this;
        }

        public Builder shutdownTimeoutMs(long shutdownTimeoutMs) {
            this.shutdownTimeoutMs = shutdownTimeoutMs;
            return this;
        }

        public Builder maxConcurrentRequests(int maxConcurrentRequests) {
            this.maxConcurrentRequests = maxConcurrentRequests;
            return this;
        }

        /**
         * Builds the LogdashConfig instance with the configured settings.
         *
         * @return a new LogdashConfig instance
         * @throws LogdashException if configuration is invalid
         */
        public LogdashConfig build() {
            return new LogdashConfig(
                    apiKey,
                    baseUrl,
                    enableConsoleOutput,
                    enableVerboseLogging,
                    maxRetries,
                    retryDelayMs,
                    requestTimeoutMs,
                    shutdownTimeoutMs,
                    maxConcurrentRequests);
        }
    }
}
