package io.logdash.check;

import io.logdash.sdk.Logdash;

import java.util.Map;

public class Check {
    public static void main(String[] args) {
        System.out.println("=== LogDash Java SDK Demo ===");
        
        // Get package version (would need to be handled differently in a real scenario)
        System.out.println("Using logdash package version: " + getLogdashVersion());
        System.out.println();
        
        String apiKey = System.getenv("LOGDASH_API_KEY");
        String logsSeed = System.getenv().getOrDefault("LOGS_SEED", "default");
        String metricsSeed = System.getenv().getOrDefault("METRICS_SEED", "1");
        
        System.out.println("Using API Key: " + apiKey);
        System.out.println("Using Logs Seed: " + logsSeed);
        System.out.println("Using Metrics Seed: " + metricsSeed);
        
        try (Logdash logdash = Logdash.create(apiKey)) {
            var logger = logdash.logger();
            var metrics = logdash.metrics();
            
            // Log some messages with seed appended
            logger.info("This is an info log " + logsSeed);
            logger.error("This is an error log " + logsSeed);
            logger.warn("This is a warning log " + logsSeed);
            logger.debug("This is a debug log " + logsSeed);
            logger.http("This is a http log " + logsSeed);
            logger.silly("This is a silly log " + logsSeed);
            logger.info("This is an info log " + logsSeed);
            logger.verbose("This is a verbose log " + logsSeed);
            
            // Set and mutate metrics with seed
            int seedValue = Integer.parseInt(metricsSeed);
            metrics.set("users", seedValue);
            metrics.mutate("users", 1);

            // Wait to ensure data is sent
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private static String getLogdashVersion() {
        try {
            return Check.class.getPackage().getImplementationVersion();
        } catch (Exception e) {
            return "unknown";
        }
    }
} 