package io.logdash.check;

import io.logdash.sdk.Logdash;

/**
 * Sends a log of each level and a metric with the SDK as published, for run.sh to read back.
 */
public class Check {
    public static void main(String[] args) {
        String logsSeed = System.getenv().getOrDefault("LOGS_SEED", "default");
        int metricsSeed = Integer.parseInt(System.getenv().getOrDefault("METRICS_SEED", "1"));

        System.out.println("=== Logdash Java SDK check ===");
        System.out.println("Using logdash " + System.getenv("LOGDASH_VERSION"));
        System.out.println("Using logs seed: " + logsSeed);
        System.out.println("Using metrics seed: " + metricsSeed);

        try (Logdash logdash =
                Logdash.builder()
                        .apiKey(System.getenv("LOGDASH_API_KEY"))
                        .baseUrl(System.getenv().getOrDefault("LOGDASH_API_URL", "https://api.logdash.io"))
                        .app("java-sdk")
                        .build()) {
            var logger = logdash.logger();
            var metrics = logdash.metrics();

            logger.info("This is an info log " + logsSeed);
            logger.error("This is an error log " + logsSeed);
            logger.warn("This is a warning log " + logsSeed);
            logger.debug("This is a debug log " + logsSeed);
            logger.http("This is a http log " + logsSeed);
            logger.silly("This is a silly log " + logsSeed);
            logger.info("This is an info log " + logsSeed);
            logger.verbose("This is a verbose log " + logsSeed);

            metrics.set("users_java", metricsSeed);
            // The mutate adds to the set, so the set must arrive first
            logdash.flush();
            metrics.mutate("users_java", 1);
        } // close sends what is still queued
    }
}
