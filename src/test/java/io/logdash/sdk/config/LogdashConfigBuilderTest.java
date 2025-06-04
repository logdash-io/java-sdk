package io.logdash.sdk.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class LogdashConfigBuilderTest {

    @Test
    void should_chain_builder_methods_correctly() {
        // Act
        var builder =
                LogdashConfig.builder()
                        .apiKey("test")
                        .baseUrl("https://test.com")
                        .enableConsoleOutput(false)
                        .enableVerboseLogging(true)
                        .maxRetries(5)
                        .retryDelayMs(1000L)
                        .requestTimeoutMs(5000L)
                        .shutdownTimeoutMs(3000L)
                        .maxConcurrentRequests(20);

        // Assert
        assertThat(builder).isNotNull();

        var config = builder.build();
        assertThat(config.apiKey()).isEqualTo("test");
        assertThat(config.baseUrl()).isEqualTo("https://test.com");
        assertThat(config.enableConsoleOutput()).isFalse();
        assertThat(config.enableVerboseLogging()).isTrue();
        assertThat(config.maxRetries()).isEqualTo(5);
        assertThat(config.retryDelayMs()).isEqualTo(1000L);
        assertThat(config.requestTimeoutMs()).isEqualTo(5000L);
        assertThat(config.shutdownTimeoutMs()).isEqualTo(3000L);
        assertThat(config.maxConcurrentRequests()).isEqualTo(20);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "https://api.example.com",
                    "http://localhost:8080",
                    "https://subdomain.example.org/path",
                    "relative-path"
            })
    void should_accept_various_url_formats(String url) {
        // Act & Assert
        assertThatNoException().isThrownBy(() -> LogdashConfig.builder().baseUrl(url).build());
    }

    @Test
    void should_validate_extreme_timeout_values() {
        // Very large values should be accepted
        assertThatNoException()
                .isThrownBy(
                        () ->
                                LogdashConfig.builder()
                                        .requestTimeoutMs(Long.MAX_VALUE)
                                        .shutdownTimeoutMs(Long.MAX_VALUE)
                                        .retryDelayMs(Long.MAX_VALUE)
                                        .build());

        // Boundary values
        assertThatNoException()
                .isThrownBy(
                        () ->
                                LogdashConfig.builder()
                                        .requestTimeoutMs(1L)
                                        .shutdownTimeoutMs(1L)
                                        .retryDelayMs(0L)
                                        .maxRetries(0)
                                        .maxConcurrentRequests(1)
                                        .build());
    }
}
