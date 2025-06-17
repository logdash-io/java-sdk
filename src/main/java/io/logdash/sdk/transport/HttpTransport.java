package io.logdash.sdk.transport;

import io.logdash.sdk.config.LogdashConfig;
import io.logdash.sdk.exception.LogdashException;
import io.logdash.sdk.log.LogEntry;
import io.logdash.sdk.metrics.MetricEntry;
import io.logdash.sdk.util.JsonSerializer;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance HTTP transport implementation for sending observability data to Logdash
 * platform.
 *
 * <p>This transport is optimized for throughput and low latency with:
 *
 * <ul>
 *   <li>Non-blocking concurrency control using tryAcquire
 *   <li>Efficient request tracking with CountDownLatch
 *   <li>Optimized thread pool sizing
 *   <li>Fast retry logic with exponential backoff
 *   <li>Minimal synchronization overhead
 * </ul>
 */
public final class HttpTransport implements LogdashTransport, AutoCloseable {

    private static final String LOGS_ENDPOINT = "/logs";
    private static final String METRICS_ENDPOINT = "/metrics";

    private static final String API_KEY_HEADER = "project-api-key";
    private static final String USER_AGENT = "logdash-java-sdk/0.2.0-SNAPSHOT";
    private static final String CONTENT_TYPE = "application/json";

    private final LogdashConfig config;
    private final JsonSerializer serializer;
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final Semaphore rateLimiter;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final AtomicLong activeRequests = new AtomicLong(0);

    private volatile CountDownLatch pendingRequestsLatch = new CountDownLatch(0);

    /**
     * Creates a new HTTP transport with the specified configuration.
     *
     * @param config the configuration for HTTP operations
     * @throws LogdashException if transport initialization fails
     */
    public HttpTransport(LogdashConfig config) {
        this.config = config;
        this.serializer = new JsonSerializer();
        this.rateLimiter = new Semaphore(config.maxConcurrentRequests(), true);
        this.executorService = createOptimizedExecutorService();
        this.httpClient = createOptimizedHttpClient();

        if (config.enableVerboseLogging()) {
            System.out.println("HTTP transport initialized for " + config.baseUrl());
        }
    }

    @Override
    public CompletableFuture<Void> sendLog(LogEntry logEntry) {
        if (shutdownInitiated.get() || closed.get()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            String json = serializer.serialize(logEntry);
            return sendWithRetry(LOGS_ENDPOINT, json, "POST");
        } catch (Exception e) {
            handleTransportError("Failed to serialize log entry", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Void> sendMetric(MetricEntry metricEntry) {
        if (shutdownInitiated.get() || closed.get()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            String json = serializer.serialize(metricEntry);
            return sendWithRetry(METRICS_ENDPOINT, json, "PUT");
        } catch (Exception e) {
            handleTransportError("Failed to serialize metric entry", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Sends an HTTP request with optimized retry logic and smart concurrency control. Uses tryAcquire
     * with timeout instead of immediate rejection for better throughput.
     *
     * @param endpoint the API endpoint path
     * @param json     the JSON payload to send
     * @param method   the HTTP method (POST or PUT)
     * @return a CompletableFuture that completes when the request succeeds or all retries are exhausted
     */
    private CompletableFuture<Void> sendWithRetry(String endpoint, String json, String method) {
        if (shutdownInitiated.get() || closed.get()) {
            return CompletableFuture.completedFuture(null);
        }

        final long sequenceId = requestCounter.incrementAndGet();

        return CompletableFuture.runAsync(
                () -> {
                    boolean acquired = false;
                    try {
                        acquired = rateLimiter.tryAcquire(200, TimeUnit.MILLISECONDS);
                        if (!acquired) {
                            if (config.enableVerboseLogging()) {
                                System.out.printf(
                                        "Request #%d rejected due to concurrency limit timeout%n", sequenceId);
                            }
                            return;
                        }

                        if (shutdownInitiated.get() || closed.get()) {
                            return;
                        }

                        long currentActive = activeRequests.incrementAndGet();
                        if (currentActive == 1) {
                            pendingRequestsLatch = new CountDownLatch(1);
                        }

                        Exception lastException = null;
                        for (int attempt = 1; attempt <= config.maxRetries() + 1; attempt++) {
                            try {
                                sendHttpRequest(endpoint, json, method, attempt, sequenceId);
                                return;
                            } catch (Exception e) {
                                lastException = e;
                                if (config.enableVerboseLogging()) {
                                    System.err.printf(
                                            "Request #%d failed: %s (attempt %d/%d)%n",
                                            sequenceId, e.getMessage(), attempt, config.maxRetries() + 1);
                                }

                                if (attempt <= config.maxRetries() && !shutdownInitiated.get() && !closed.get()) {
                                    try {
                                        long baseDelay = config.retryDelayMs();
                                        long exponentialDelay = baseDelay * (1L << (attempt - 1));
                                        long maxDelay = Math.min(exponentialDelay, 5000);
                                        long jitter = (long) (Math.random() * 100);
                                        Thread.sleep(maxDelay + jitter);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        return;
                                    }
                                }
                            }
                        }

                        handleTransportError(
                                String.format(
                                        "Request #%d failed permanently after %d attempts",
                                        sequenceId, config.maxRetries() + 1),
                                lastException);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        if (acquired) {
                            rateLimiter.release();
                        }

                        long remaining = activeRequests.decrementAndGet();
                        if (remaining == 0) {
                            pendingRequestsLatch.countDown();
                        }
                    }
                },
                executorService);
    }

    private void sendHttpRequest(
            String endpoint,
            String json,
            String method,
            int attempt,
            long sequenceId) throws InterruptedException {
        URI uri;
        try {
            uri = createEndpointUri(endpoint);
        } catch (LogdashException e) {
            throw e;
        } catch (Exception e) {
            throw new LogdashException("Failed to create endpoint URI", e);
        }

        if (config.enableVerboseLogging()) {
            logRequestStart(method, endpoint, sequenceId, json.length(), attempt);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                    .header("Content-Type", CONTENT_TYPE)
                    .header(API_KEY_HEADER, config.apiKey())
                    .header("User-Agent", USER_AGENT)
                    .method(method, HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (isSuccessResponse(response.statusCode())) {
                if (config.enableVerboseLogging()) {
                    System.out.printf(
                            "Successfully sent request #%d to %s (status=%d)%n",
                            sequenceId, endpoint, response.statusCode());
                }
            } else {
                throw new LogdashException("HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException e) {
            throw new LogdashException("I/O error during HTTP request", e);
        }
    }

    /**
     * Flushes all pending requests efficiently using CountDownLatch.
     *
     * @return a future that completes when all pending requests finish
     */
    public CompletableFuture<Void> flush() {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        CountDownLatch currentLatch = pendingRequestsLatch;
                        if (currentLatch != null && activeRequests.get() > 0) {
                            long timeoutMs = Math.min(config.shutdownTimeoutMs(), 5000);
                            boolean completed = currentLatch.await(timeoutMs, TimeUnit.MILLISECONDS);

                            if (completed && config.enableVerboseLogging()) {
                                System.out.println("All pending requests flushed successfully");
                            } else if (!completed && config.enableVerboseLogging()) {
                                System.out.printf(
                                        "Flush timeout, %d requests still pending%n", activeRequests.get());
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                executorService);
    }

    @Override
    public Optional<CompletableFuture<Void>> shutdown() {
        if (shutdownInitiated.compareAndSet(false, true)) {
            return Optional.of(
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    if (config.enableVerboseLogging()) {
                                        System.out.println("Initiating HTTP transport shutdown...");
                                    }

                                    CountDownLatch currentLatch = pendingRequestsLatch;
                                    long timeoutMs = Math.min(config.shutdownTimeoutMs(), 3000);

                                    if (currentLatch != null && activeRequests.get() > 0) {
                                        if (config.enableVerboseLogging()) {
                                            System.out.printf(
                                                    "Waiting for %d pending HTTP requests...%n", activeRequests.get());
                                        }

                                        boolean completed = currentLatch.await(timeoutMs, TimeUnit.MILLISECONDS);

                                        if (completed && config.enableVerboseLogging()) {
                                            System.out.println("All HTTP requests completed successfully");
                                        } else if (!completed && config.enableVerboseLogging()) {
                                            System.out.printf(
                                                    "Shutdown timeout exceeded, %d requests may be lost%n",
                                                    activeRequests.get());
                                        }
                                    }

                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                } finally {
                                    closed.set(true);
                                }
                            },
                            executorService));
        }
        return Optional.of(CompletableFuture.completedFuture(null));
    }

    @Override
    public void close() {
        shutdownInitiated.set(true);
        closed.set(true);

        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                if (config.enableVerboseLogging()) {
                    System.out.println("Forcing HTTP executor shutdown");
                }
                executorService.shutdownNow();
            }

            if (config.enableVerboseLogging()) {
                System.out.println("HTTP transport closed");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    /**
     * Creates an optimized thread pool for HTTP operations. Uses more aggressive sizing for better
     * throughput.
     */
    private ExecutorService createOptimizedExecutorService() {
        int corePoolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        int maxPoolSize = Math.max(config.maxConcurrentRequests(), corePoolSize * 2);

        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(
                        corePoolSize,
                        maxPoolSize,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(config.maxConcurrentRequests() * 2),
                        r -> {
                            Thread t = new Thread(r, "logdash-http-" + System.currentTimeMillis());
                            t.setDaemon(true);
                            return t;
                        },
                        new ThreadPoolExecutor.CallerRunsPolicy());

        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private HttpClient createOptimizedHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(config.requestTimeoutMs(), 10000)))
                .build();
    }

    private URI createEndpointUri(String endpoint) {
        try {
            String baseUrl =
                    config.baseUrl().endsWith("/")
                            ? config.baseUrl().substring(0, config.baseUrl().length() - 1)
                            : config.baseUrl();
            return new URI(baseUrl + endpoint);
        } catch (URISyntaxException e) {
            throw new LogdashException("Invalid endpoint URI: " + config.baseUrl() + endpoint, e);
        }
    }

    private boolean isSuccessResponse(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    private void logRequestStart(
            String method, String endpoint, long requestId, int bodySize, int attempt) {
        System.out.printf(
                "Sending HTTP %s request #%d to %s (attempt %d): headers={Content-Type=[%s], %s=[%s]}, bodySize=%d%n",
                method,
                requestId,
                endpoint,
                attempt,
                CONTENT_TYPE,
                API_KEY_HEADER,
                maskApiKey(config.apiKey()),
                bodySize);
    }

    private void handleTransportError(String message, Throwable cause) {
        if (config.enableVerboseLogging()) {
            System.err.println("Logdash transport error: " + message);
            if (cause != null) {
                System.err.println("Cause: " + cause.getMessage());
            }
        }
    }
}
