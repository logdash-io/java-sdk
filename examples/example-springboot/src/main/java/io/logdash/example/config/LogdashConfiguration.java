package io.logdash.example.config;

import io.logdash.sdk.Logdash;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.logdash.example.config.LogdashConfiguration.LogdashProperties;

@Configuration
@EnableConfigurationProperties(LogdashProperties.class)
public class LogdashConfiguration {

    @Bean
    public Logdash logdash(LogdashProperties properties) {
        return Logdash.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .enableConsoleOutput(properties.enableConsoleOutput())
                .enableVerboseLogging(properties.enableVerboseLogging())
                .requestTimeoutMs(properties.requestTimeoutMs())
                .maxConcurrentRequests(properties.maxConcurrentRequests())
                .build();
    }

    @ConfigurationProperties(prefix = "logdash")
    public record LogdashProperties(
            String apiKey,
            String baseUrl,
            boolean enableConsoleOutput,
            boolean enableVerboseLogging,
            long requestTimeoutMs,
            int maxConcurrentRequests
    ) {
        public LogdashProperties {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("Logdash API key is required");
            }
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.logdash.io";
            }
            if (requestTimeoutMs <= 0) {
                requestTimeoutMs = 10000L;
            }
            if (maxConcurrentRequests <= 0) {
                maxConcurrentRequests = 20;
            }
        }
    }
}
