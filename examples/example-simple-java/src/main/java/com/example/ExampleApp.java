package com.example;

import io.logdash.sdk.Logdash;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ExampleApp {

    public static final String METRIC_ACTIVE_USERS = "active_users";
    public static final String METRIC_API_REQUESTS = "api_requests";

    public static void main(String[] args) {
        var random = new Random();
        Logdash logdash = Logdash.builder()
                .apiKey("your-api-key")
                .baseUrl("https://api.logdash.io")
                .enableConsoleOutput(true)
                .enableVerboseLogging(false)
                .requestTimeoutMs(10000)
                .maxConcurrentRequests(20)
                .shutdownTimeoutMs(15000)
                .build();

        var logger = logdash.logger();
        var metrics = logdash.metrics();

        logger.info("Application started", Map.of("version", "1.0.0"));

        for (int i = 0; i < 2; i++) {
            int users = 50 + random.nextInt(51);
            int apiCalls = random.nextInt(10) + 1;

            metrics.set(METRIC_ACTIVE_USERS, users);
            metrics.mutate(METRIC_ACTIVE_USERS, 100);
            metrics.mutate(METRIC_API_REQUESTS, apiCalls);

            logger.debug("Iteration " + i, Map.of("users", users, "apiCalls", apiCalls));

            try {
                TimeUnit.MILLISECONDS.sleep(1000L + random.nextInt(3000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        logger.http("Processed API request");
        logger.warn("Random warning");
        logger.error("Random error");
        logger.verbose("Verbose log");
        logger.silly("Silly log");

        logger.info("Example completed");
    }
}
