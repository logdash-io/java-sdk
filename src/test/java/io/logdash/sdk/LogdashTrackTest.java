package io.logdash.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

class LogdashTrackTest {

    private static final String UUID_V4 =
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * A number that JSON serialization cannot write, since Jackson writes unknown numbers with
     * {@code toString()}.
     */
    private static final class UnprintableNumber extends Number {
        @Override
        public int intValue() {
            return 4;
        }

        @Override
        public long longValue() {
            return 4;
        }

        @Override
        public float floatValue() {
            return 4;
        }

        @Override
        public double doubleValue() {
            return 4;
        }

        @Override
        public String toString() {
            throw new IllegalStateException("toString failed");
        }
    }

    private WireMockServer wireMockServer;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    private static String stripAnsiCodes(String input) {
        return input.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        wireMockServer.stubFor(post(urlEqualTo("/events")).willReturn(aResponse().withStatus(202)));

        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        wireMockServer.stop();
    }

    private Logdash.Builder logdashBuilder() {
        return Logdash.builder()
                .apiKey("test-api-key")
                .baseUrl("http://localhost:" + wireMockServer.port())
                .enableConsoleOutput(false)
                .retryDelayMs(50L);
    }

    private List<String> sentEventIds() {
        return wireMockServer.findAll(postRequestedFor(urlEqualTo("/events"))).stream()
                .map(request -> readJson(request.getBodyAsString()).get("id").asText())
                .toList();
    }

    private static JsonNode readJson(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void should_send_event_to_events_endpoint() {
        // Act
        try (var logdash = logdashBuilder().build()) {
            logdash.track("agent_run", Map.of("agent", "support", "steps", 4, "success", true), "42");
            logdash.flush();
        }

        // Assert
        wireMockServer.verify(
                1,
                postRequestedFor(urlEqualTo("/events"))
                        .withHeader("project-api-key", equalTo("test-api-key"))
                        .withHeader("Content-Type", equalTo("application/json"))
                        .withRequestBody(
                                equalToJson(
                                        """
                                        {
                                          "name": "agent_run",
                                          "props": {"agent": "support", "steps": 4, "success": true},
                                          "userId": "42",
                                          "id": "${json-unit.regex}%s"
                                        }
                                        """
                                                .formatted(UUID_V4))));
    }

    @Test
    void should_leave_props_user_id_and_app_out_when_unset() {
        // Act
        try (var logdash = logdashBuilder().build()) {
            logdash.track("signup");
            logdash.flush();
        }

        // Assert
        wireMockServer.verify(
                1,
                postRequestedFor(urlEqualTo("/events"))
                        .withRequestBody(
                                equalToJson(
                                        "{\"name\": \"signup\", \"id\": \"${json-unit.regex}%s\"}"
                                                .formatted(UUID_V4))));
    }

    @Test
    void should_send_app_with_events_when_set() {
        // Act
        try (var logdash = logdashBuilder().app("desktop").build()) {
            logdash.track("agent_run", Map.of("steps", 4));
            logdash.flush();
        }

        // Assert
        wireMockServer.verify(
                1,
                postRequestedFor(urlEqualTo("/events"))
                        .withRequestBody(matchingJsonPath("$.app", equalTo("desktop")))
                        .withRequestBody(matchingJsonPath("$.props.steps", equalTo("4"))));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Agent_Run", "agent-run", "1st_run", "pageview", "browser_error"})
    void should_drop_event_with_invalid_name_with_warning(String name) {
        // Act
        try (var logdash = logdashBuilder().enableVerboseLogging(true).build()) {
            logdash.track(name, Map.of("steps", 4), "42");
            logdash.flush();
        }

        // Assert
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/events")));
        assertThat(capturedErr.toString()).contains("Warning: Event dropped: Event name");
    }

    @Test
    void should_drop_event_with_email_as_user_id_with_warning() {
        // Act
        try (var logdash = logdashBuilder().enableVerboseLogging(true).build()) {
            logdash.track("signup", Map.of(), "ada@example.com");
            logdash.flush();
        }

        // Assert
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/events")));
        assertThat(capturedErr.toString())
                .contains("Warning: Event dropped: User ID of event signup must be an internal ID")
                .doesNotContain("ada@example.com");
    }

    @Test
    void should_drop_event_instead_of_throwing_when_a_prop_fails() {
        // Arrange
        var failingValue =
                new Object() {
                    @Override
                    public String toString() {
                        throw new IllegalStateException("toString failed");
                    }
                };

        // Act
        try (var logdash = logdashBuilder().enableVerboseLogging(true).build()) {
            logdash.track("agent_run", Map.of("agent", failingValue));
            logdash.flush();
        }

        // Assert
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/events")));
        assertThat(capturedErr.toString()).contains("Warning: Event dropped: toString failed");
    }

    @Test
    void should_send_tracked_events_on_flush() {
        try (var logdash = logdashBuilder().build()) {
            // Act
            for (int step = 0; step < 5; step++) {
                logdash.track("agent_step", Map.of("step", step));
            }
            logdash.flush();

            // Assert, before close sends what is left
            wireMockServer.verify(5, postRequestedFor(urlEqualTo("/events")));
            assertThat(sentEventIds()).doesNotHaveDuplicates().allMatch(id -> id.matches(UUID_V4));
        }
    }

    @Test
    void should_send_tracked_events_on_close() {
        // Arrange
        var logdash = logdashBuilder().build();
        logdash.track("agent_run");

        // Act
        logdash.close();

        // Assert
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/events")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_drop_events_instead_of_blocking_when_the_send_queue_is_full(boolean verbose) {
        // Arrange: a slow response holds the only request slot, so the queue fills up
        wireMockServer.stubFor(
                post(urlEqualTo("/events")).willReturn(aResponse().withStatus(202).withFixedDelay(2000)));

        try (var logdash =
                     logdashBuilder()
                             .maxConcurrentRequests(1)
                             .shutdownTimeoutMs(500L)
                             .enableVerboseLogging(verbose)
                             .build()) {
            // Act
            long start = System.nanoTime();
            for (int i = 0; i < 500; i++) {
                logdash.track("agent_run");
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            // Assert
            assertThat(elapsedMs).isLessThan(1000);
        }
        assertThat(capturedOut.toString().contains("rejected because the send queue is full"))
                .isEqualTo(verbose);
    }

    @Test
    void should_drop_invalid_event_silently_without_verbose_logging() {
        // Act
        try (var logdash = logdashBuilder().build()) {
            logdash.track("Agent_Run");
            logdash.flush();
        }

        // Assert
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/events")));
        assertThat(capturedErr.toString()).doesNotContain("Event dropped");
    }

    @Test
    void should_drop_events_tracked_after_close() {
        // Arrange
        var logdash = logdashBuilder().build();
        logdash.close();

        // Act
        logdash.track("agent_run");

        // Assert
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/events")));
    }

    @Test
    void should_drop_event_whose_props_fail_to_serialize() {
        // Act
        try (var logdash = logdashBuilder().enableVerboseLogging(true).build()) {
            logdash.track("agent_run", Map.of("steps", new UnprintableNumber()));
            logdash.flush();
        }

        // Assert
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/events")));
        assertThat(capturedErr.toString()).contains("Failed to serialize event entry");
    }

    @Test
    void should_reuse_event_id_when_retrying() {
        // Arrange
        wireMockServer.stubFor(
                post(urlEqualTo("/events"))
                        .inScenario("retry")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(aResponse().withStatus(503))
                        .willSetStateTo("retried"));
        wireMockServer.stubFor(
                post(urlEqualTo("/events"))
                        .inScenario("retry")
                        .whenScenarioStateIs("retried")
                        .willReturn(aResponse().withStatus(202)));

        // Act
        try (var logdash = logdashBuilder().build()) {
            logdash.track("agent_run");
            logdash.flush();
        }

        // Assert
        var ids = sentEventIds();
        assertThat(ids).hasSize(2).allMatch(id -> id.matches(UUID_V4));
        assertThat(ids.get(1)).isEqualTo(ids.get(0));
    }

    @Test
    void should_print_event_to_console_without_api_key() {
        // Act
        try (var logdash =
                     Logdash.builder().baseUrl("http://localhost:" + wireMockServer.port()).build()) {
            logdash.track("agent_run", Map.of("agent", "support"), "42");
        }

        // Assert
        assertThat(stripAnsiCodes(capturedOut.toString()))
                .contains("[EVENT] agent_run {agent=support} (userId=42)");
        wireMockServer.verify(0, anyRequestedFor(anyUrl()));
    }
}
