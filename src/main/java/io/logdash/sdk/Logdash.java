package io.logdash.sdk;

import io.logdash.sdk.config.LogdashConfig;
import io.logdash.sdk.event.EventEntry;
import io.logdash.sdk.log.LogdashLogger;
import io.logdash.sdk.metrics.LogdashMetrics;
import io.logdash.sdk.transport.HttpTransport;
import io.logdash.sdk.transport.LogdashTransport;
import io.logdash.sdk.transport.NoOpTransport;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Main entry point for the Logdash Java SDK.
 *
 * <p>This class provides access to logging, metrics and event tracking for sending observability
 * data to the Logdash.io platform. It manages the underlying transport layer and provides a simple
 * API for developers to integrate observability into their applications.
 *
 * <p>The SDK automatically handles HTTP transport with retry logic, rate limiting, and graceful
 * shutdown. If no API key is provided or HTTP transport fails, it falls back to console-only mode.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Simple usage with API key
 * try (var logdash = Logdash.create("your-api-key")) {
 *     logdash.logger().info("Application started");
 *     logdash.metrics().mutate("app.starts", 1);
 * }
 *
 * // Advanced configuration
 * try (var logdash = Logdash.builder()
 *         .apiKey("your-api-key")
 *         .enableVerboseLogging(true)
 *         .maxRetries(5)
 *         .build()) {
 *
 *     logdash.logger().error("Error occurred", Map.of("userId", 123));
 *     logdash.metrics().set("active_users", 1500);
 * }
 * }</pre>
 */
public final class Logdash implements AutoCloseable {

    private final LogdashConfig config;
    private final LogdashTransport transport;
    private final LogdashLogger logger;
    private final Thread shutdownHook;
    private volatile boolean closed = false;

    private Logdash(LogdashConfig config) {
        this.config = config;
        this.transport = createTransport(config);
        this.logger = new LogdashLogger(config, transport);

        this.shutdownHook = new Thread(this::performShutdownHookCleanup, "logdash-shutdown-hook");
        this.shutdownHook.setPriority(Thread.MAX_PRIORITY);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * Creates a new builder for configuring Logdash instances.
     *
     * @return a new builder instance for customizing SDK configuration
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a Logdash instance with default configuration and the specified API key.
     *
     * @param apiKey the API key for authenticating with Logdash.io
     * @return a new Logdash instance ready for use
     * @throws IllegalArgumentException if apiKey is null or blank
     */
    public static Logdash create(String apiKey) {
        return new Logdash(LogdashConfig.builder().apiKey(apiKey).build());
    }

    /**
     * Returns the logger instance for sending structured log messages.
     *
     * @return the logger instance
     * @throws IllegalStateException if this instance has been closed
     */
    public LogdashLogger logger() {
        checkNotClosed();
        return logger;
    }

    /**
     * Returns the metrics instance for sending custom metrics and measurements.
     *
     * @return a new metrics instance (defensive copy)
     * @throws IllegalStateException if this instance has been closed
     */
    public LogdashMetrics metrics() {
        checkNotClosed();
        return new LogdashMetrics(config, transport);
    }

    /**
     * Tracks an event without properties or a user.
     *
     * @param name the event name, matching {@code ^[a-z][a-z0-9_]{0,63}$}
     * @see #track(String, Map, String)
     */
    public void track(String name) {
        track(name, Map.of(), null);
    }

    /**
     * Tracks an event with properties and no user.
     *
     * @param name  the event name, matching {@code ^[a-z][a-z0-9_]{0,63}$}
     * @param props flat properties with string, number or boolean values
     * @see #track(String, Map, String)
     */
    public void track(String name, Map<String, Object> props) {
        track(name, props, null);
    }

    /**
     * Tracks an event, such as an agent run, for the domain's Analytics.
     *
     * <p>Like logs, the event is sent in the background, and this method never throws. Flush and
     * close send pending events too. Without an API key, the event is printed to the console only.
     *
     * <p>An invalid name, a name of the website tracker ({@code pageview}, {@code pageleave} or
     * {@code browser_error}), or a user ID that contains {@code @} drops the event, with a warning
     * when verbose logging is on.
     *
     * <pre>{@code
     * logdash.track("agent_run", Map.of("agent", "support", "steps", 4), user.id());
     * }</pre>
     *
     * @param name   the event name, matching {@code ^[a-z][a-z0-9_]{0,63}$}
     * @param props  up to 10 flat properties with keys matching {@code ^[a-z][a-z0-9_]{0,39}$} and
     *               string, number or boolean values; never personal data
     * @param userId the signed-in user's internal ID, never an email address, so one person counts
     *               once across the website and apps; null counts a visitor of this app that
     *               changes every day
     */
    public void track(String name, Map<String, Object> props, String userId) {
        try {
            transport.sendEvent(new EventEntry(name, props, userId));
        } catch (RuntimeException e) {
            if (config.enableVerboseLogging()) {
                System.err.println("Warning: Event dropped: " + e.getMessage());
            }
        }
    }

    /**
     * Returns the configuration used by this instance.
     *
     * @return the immutable configuration object
     */
    public LogdashConfig config() {
        return config;
    }

    /**
     * Flushes all pending logs, metrics and events synchronously.
     *
     * <p>Blocks until all queued requests complete or the configured timeout is reached. This method
     * is useful for ensuring all data is sent before application shutdown.
     */
    public void flush() {
        if (closed || !(transport instanceof HttpTransport httpTransport)) {
            return;
        }

        try {
            if (config.enableVerboseLogging()) {
                System.out.println("Flushing pending requests...");
            }

            httpTransport.flush().get(config.shutdownTimeoutMs(), TimeUnit.MILLISECONDS);

            if (config.enableVerboseLogging()) {
                System.out.println("Flush completed successfully");
            }
        } catch (ExecutionException | TimeoutException e) {
            if (config.enableVerboseLogging()) {
                System.err.println("Flush failed: " + e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (config.enableVerboseLogging()) {
                System.err.println("Flush interrupted: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down
            }
            closeInternal();
        }
    }

    private void performShutdownHookCleanup() {
        if (closed) {
            return;
        }

        try {
            if (config.enableVerboseLogging()) {
                System.out.println("Shutting down Logdash SDK via shutdown hook...");
            }
            performShutdown(Math.min(config.shutdownTimeoutMs(), 3000));
        } catch (Exception e) {
            if (config.enableVerboseLogging()) {
                System.err.println("Error during shutdown hook cleanup: " + e.getMessage());
            }
        }
    }

    private void closeInternal() {
        if (!closed) {
            if (config.enableVerboseLogging()) {
                System.out.println("Shutting down Logdash SDK...");
            }
            performShutdown(config.shutdownTimeoutMs());
        }
    }

    private void performShutdown(long timeoutMs) {
        if (closed) {
            return;
        }

        closed = true;

        try {
            transport
                    .shutdown()
                    .ifPresent(
                            shutdownFuture -> {
                                try {
                                    shutdownFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
                                } catch (Exception e) {
                                    if (config.enableVerboseLogging()) {
                                        System.err.println("Warning: Graceful shutdown timeout: " + e.getMessage());
                                    }
                                }
                            });
        } finally {
            transport.close();
            if (config.enableVerboseLogging()) {
                System.out.println("Logdash SDK shutdown completed");
            }
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Logdash instance has been closed");
        }
    }

    private LogdashTransport createTransport(LogdashConfig config) {
        try {
            if (config.apiKey() == null || config.apiKey().isBlank()) {
                if (config.enableVerboseLogging()) {
                    System.out.println("No API key provided, using console-only mode");
                }
                return new NoOpTransport(config);
            }
            return new HttpTransport(config);
        } catch (Exception e) {
            if (config.enableVerboseLogging()) {
                System.err.printf(
                        "Failed to create HTTP transport, falling back to console-only: %s%n", e.getMessage());
                e.printStackTrace();
            }
            return new NoOpTransport(config);
        }
    }

    public static final class Builder {
        private String apiKey;
        private String baseUrl = "https://api.logdash.io";
        private boolean enableConsoleOutput = true;
        private boolean enableVerboseLogging = false;
        private int maxRetries = 3;
        private long retryDelayMs = 500L;
        private long requestTimeoutMs = 15000L;
        private long shutdownTimeoutMs = 10000L;
        private int maxConcurrentRequests = 20;
        private String app;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the base URL for the Logdash API.
         *
         * @param baseUrl the base URL (defaults to https://api.logdash.io)
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Enables or disables console output for logs, metrics and events.
         *
         * @param enableConsoleOutput true to enable console output
         * @return this builder
         */
        public Builder enableConsoleOutput(boolean enableConsoleOutput) {
            this.enableConsoleOutput = enableConsoleOutput;
            return this;
        }

        /**
         * Enables or disables verbose logging for debugging.
         *
         * @param enableVerboseLogging true to enable verbose logging
         * @return this builder
         */
        public Builder enableVerboseLogging(boolean enableVerboseLogging) {
            this.enableVerboseLogging = enableVerboseLogging;
            return this;
        }

        /**
         * Sets the maximum number of retry attempts for failed requests.
         *
         * @param maxRetries the maximum retry attempts (defaults to 3)
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the retry delay in milliseconds.
         *
         * @param retryDelayMs the retry delay (defaults to 500ms)
         * @return this builder
         */
        public Builder retryDelayMs(long retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
            return this;
        }

        /**
         * Sets the request timeout in milliseconds.
         *
         * @param requestTimeoutMs the request timeout (defaults to 15000ms)
         * @return this builder
         */
        public Builder requestTimeoutMs(long requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
            return this;
        }

        /**
         * Sets the shutdown timeout in milliseconds.
         *
         * @param shutdownTimeoutMs the shutdown timeout (defaults to 10000ms)
         * @return this builder
         */
        public Builder shutdownTimeoutMs(long shutdownTimeoutMs) {
            this.shutdownTimeoutMs = shutdownTimeoutMs;
            return this;
        }

        /**
         * Sets the maximum number of concurrent requests.
         *
         * @param maxConcurrentRequests the max concurrent requests (defaults to 20)
         * @return this builder
         */
        public Builder maxConcurrentRequests(int maxConcurrentRequests) {
            this.maxConcurrentRequests = maxConcurrentRequests;
            return this;
        }

        /**
         * Sets the name of the app that sends the data, such as {@code desktop}, {@code api} or
         * {@code landing}.
         *
         * <p>The domain key is shared by all apps of a domain, and {@code app} names this one, so the
         * dashboard can filter logs and metrics by app. Every log, metric and event this instance
         * sends carries it. With a domain key, the app is created on first use. Older service keys
         * ignore {@code app}.
         *
         * <pre>{@code
         * var logdash = Logdash.builder()
         *         .apiKey(System.getenv("LOGDASH_API_KEY"))
         *         .app("api")
         *         .build();
         * }</pre>
         *
         * @param app the app name (defaults to none; blank means none; the server cuts it to 64
         *            characters)
         * @return this builder
         */
        public Builder app(String app) {
            this.app = app;
            return this;
        }

        /**
         * Builds the Logdash instance with the configured settings.
         *
         * @return a new Logdash instance
         * @throws IllegalArgumentException if configuration is invalid
         */
        public Logdash build() {
            if (requestTimeoutMs <= 0) {
                throw new IllegalArgumentException("Request timeout must be positive");
            }
            if (maxConcurrentRequests <= 0) {
                throw new IllegalArgumentException("Max concurrent requests must be positive");
            }

            LogdashConfig config =
                    new LogdashConfig(
                            apiKey,
                            baseUrl,
                            enableConsoleOutput,
                            enableVerboseLogging,
                            maxRetries,
                            retryDelayMs,
                            requestTimeoutMs,
                            shutdownTimeoutMs,
                            maxConcurrentRequests,
                            app);
            return new Logdash(config);
        }
    }
}
