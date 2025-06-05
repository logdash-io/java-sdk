package io.logdash.sdk.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogLevelTest {

    @Test
    void should_have_correct_values() {
        assertThat(LogLevel.ERROR.getValue()).isEqualTo("error");
        assertThat(LogLevel.WARN.getValue()).isEqualTo("warning");
        assertThat(LogLevel.INFO.getValue()).isEqualTo("info");
        assertThat(LogLevel.HTTP.getValue()).isEqualTo("http");
        assertThat(LogLevel.VERBOSE.getValue()).isEqualTo("verbose");
        assertThat(LogLevel.DEBUG.getValue()).isEqualTo("debug");
        assertThat(LogLevel.SILLY.getValue()).isEqualTo("silly");
    }

    @Test
    void should_return_correct_string_representation() {
        assertThat(LogLevel.ERROR.toString()).isEqualTo("error");
        assertThat(LogLevel.INFO.toString()).isEqualTo("info");
        assertThat(LogLevel.DEBUG.toString()).isEqualTo("debug");
    }

    @Test
    void should_have_all_expected_levels() {
        var levels = LogLevel.values();
        assertThat(levels).hasSize(7);
        assertThat(levels)
                .contains(
                        LogLevel.ERROR,
                        LogLevel.WARN,
                        LogLevel.INFO,
                        LogLevel.HTTP,
                        LogLevel.VERBOSE,
                        LogLevel.DEBUG,
                        LogLevel.SILLY);
    }
}
