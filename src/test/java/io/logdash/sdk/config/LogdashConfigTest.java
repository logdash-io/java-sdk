package io.logdash.sdk.config;

import io.logdash.sdk.exception.LogdashException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LogdashConfigTest {

    @Test
    void should_create_config_with_valid_parameters() {
        // Act
        var config =
                LogdashConfig.builder()
                        .apiKey("test-key")
                        .baseUrl("https://api.logdash.io")
                        .enableConsoleOutput(true)
                        .enableVerboseLogging(false)
                        .maxRetries(3)
                        .retryDelayMs(1000L)
                        .requestTimeoutMs(15000L)
                        .shutdownTimeoutMs(10000L)
                        .maxConcurrentRequests(10)
                        .build();

        // Assert
        assertThat(config.apiKey()).isEqualTo("test-key");
        assertThat(config.baseUrl()).isEqualTo("https://api.logdash.io");
        assertThat(config.enableConsoleOutput()).isTrue();
        assertThat(config.enableVerboseLogging()).isFalse();
        assertThat(config.maxRetries()).isEqualTo(3);
        assertThat(config.retryDelayMs()).isEqualTo(1000L);
        assertThat(config.requestTimeoutMs()).isEqualTo(15000L);
        assertThat(config.shutdownTimeoutMs()).isEqualTo(10000L);
        assertThat(config.maxConcurrentRequests()).isEqualTo(10);
    }

    @Test
    void should_use_default_values() {
        // Act
        var config = LogdashConfig.builder().build();

        // Assert
        assertThat(config.apiKey()).isNull();
        assertThat(config.baseUrl()).isEqualTo("https://api.logdash.io");
        assertThat(config.enableConsoleOutput()).isTrue();
        assertThat(config.enableVerboseLogging()).isFalse();
        assertThat(config.maxRetries()).isEqualTo(3);
        assertThat(config.retryDelayMs()).isEqualTo(500L);
        assertThat(config.requestTimeoutMs()).isEqualTo(15000L);
        assertThat(config.shutdownTimeoutMs()).isEqualTo(10000L);
        assertThat(config.maxConcurrentRequests()).isEqualTo(10);
    }

    @Test
    void should_throw_exception_for_null_base_url() {
        assertThatThrownBy(() ->
                LogdashConfig.builder()
                        .baseUrl(null)
                        .build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Base URL cannot be null or blank");
    }

    @Test
    void should_throw_exception_for_blank_base_url() {
        assertThatThrownBy(() ->
                LogdashConfig.builder()
                        .baseUrl("   ")
                        .build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Base URL cannot be null or blank");
    }

    @Test
    void should_accept_relative_urls_as_valid() {
        assertThatNoException().isThrownBy(() ->
                LogdashConfig.builder()
                        .baseUrl("not-a-valid-url")
                        .build()
        );
    }

    @Test
    void should_throw_exception_for_invalid_uri_syntax() {
        assertThatThrownBy(() ->
                LogdashConfig.builder()
                        .baseUrl("ht tp://invalid url with spaces")
                        .build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Invalid base URL");
    }

    @Test
    void should_throw_exception_for_negative_max_retries() {
        assertThatThrownBy(() ->
                LogdashConfig.builder()
                        .maxRetries(-1)
                        .build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Max retries cannot be negative");
    }

    @Test
    void should_throw_exception_for_negative_retry_delay() {
        assertThatThrownBy(() ->
                LogdashConfig.builder()
                        .retryDelayMs(-1L)
                        .build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Retry delay cannot be negative");
    }

    @Test
    void should_throw_exception_for_zero_or_negative_request_timeout() {
        assertThatThrownBy(() ->
                LogdashConfig.builder()
                        .requestTimeoutMs(0L)
                        .build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Request timeout must be positive");

        assertThatThrownBy(() ->
                LogdashConfig.builder()
                        .requestTimeoutMs(-1000L)
                        .build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Request timeout must be positive");
    }

    @Test
    void should_throw_exception_for_zero_or_negative_shutdown_timeout() {
        assertThatThrownBy(() ->
                LogdashConfig.builder()
                        .shutdownTimeoutMs(0L)
                        .build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Shutdown timeout must be positive");
    }

    @Test
    void should_throw_exception_for_zero_or_negative_max_concurrent_requests() {
        assertThatThrownBy(() ->
                LogdashConfig.builder()
                        .maxConcurrentRequests(0)
                        .build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Max concurrent requests must be positive");
    }

    @Test
    void should_accept_zero_max_retries() {
        assertThatNoException().isThrownBy(() ->
                LogdashConfig.builder()
                        .maxRetries(0)
                        .build()
        );
    }

    @Test
    void should_accept_zero_retry_delay() {
        assertThatNoException().isThrownBy(() ->
                LogdashConfig.builder()
                        .retryDelayMs(0L)
                        .build()
        );
    }

    @Test
    void should_validate_all_parameters_in_constructor() {
        assertThatThrownBy(() ->
                new LogdashConfig(
                        "test-key",
                        "",
                        true,
                        false,
                        3,
                        1000L,
                        15000L,
                        10000L,
                        10
                )
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Base URL cannot be null or blank");
    }

    @Test
    void should_create_valid_urls_for_common_protocols() {
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            LogdashConfig.builder().baseUrl("https://api.logdash.io").build();
                            LogdashConfig.builder().baseUrl("http://localhost:8080").build();
                            LogdashConfig.builder().baseUrl("https://custom-domain.com/api/v1").build();
                        });
    }

    @Test
    void should_validate_url_schemes_correctly() {
        // Act & Assert
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            LogdashConfig.builder().baseUrl("https://api.logdash.io").build();
                            LogdashConfig.builder().baseUrl("http://localhost:8080").build();
                            LogdashConfig.builder().baseUrl("HTTP://EXAMPLE.COM").build();
                            LogdashConfig.builder().baseUrl("HTTPS://EXAMPLE.COM").build();
                        });
    }

    @Test
    void should_handle_edge_case_timeout_values() {
        // Act & Assert
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            LogdashConfig.builder()
                                    .requestTimeoutMs(1L)
                                    .shutdownTimeoutMs(1L)
                                    .retryDelayMs(0L)
                                    .maxRetries(0)
                                    .maxConcurrentRequests(1)
                                    .build();
                        });
    }

    @Test
    void should_validate_concurrent_requests_limits() {
        assertThatThrownBy(() ->
                LogdashConfig.builder()
                        .maxConcurrentRequests(-1)
                        .build()
        ).isInstanceOf(LogdashException.class)
                .hasMessageContaining("Max concurrent requests must be positive");
    }

    @Test
    void should_handle_builder_method_chaining() {
        // Act
        var config =
                LogdashConfig.builder()
                        .apiKey("key")
                        .baseUrl("https://test.com")
                        .enableConsoleOutput(false)
                        .enableVerboseLogging(true)
                        .maxRetries(5)
                        .retryDelayMs(2000L)
                        .requestTimeoutMs(30000L)
                        .shutdownTimeoutMs(15000L)
                        .maxConcurrentRequests(50);

        // Assert
        assertThatNoException().isThrownBy(config::build);

        var builtConfig = config.build();
        assertThat(builtConfig.apiKey()).isEqualTo("key");
        assertThat(builtConfig.maxConcurrentRequests()).isEqualTo(50);
    }
}
