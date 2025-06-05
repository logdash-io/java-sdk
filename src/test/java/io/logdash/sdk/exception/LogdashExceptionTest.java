package io.logdash.sdk.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogdashExceptionTest {

    @Test
    void should_create_exception_with_message() {
        // Arrange
        var message = "Test error message";

        // Act
        var exception = new LogdashException(message);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void should_create_exception_with_message_and_cause() {
        // Arrange
        var message = "Test error message";
        var cause = new RuntimeException("Root cause");

        // Act
        var exception = new LogdashException(message, cause);

        // Assert
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void should_be_runtime_exception() {
        var exception = new LogdashException("Test");
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
