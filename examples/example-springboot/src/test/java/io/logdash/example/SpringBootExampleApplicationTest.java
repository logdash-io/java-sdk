package io.logdash.example;

import io.logdash.sdk.Logdash;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "logdash.api-key=test-key",
        "logdash.enable-console-output=false",
        "logdash.enable-verbose-logging=false"
})
class SpringBootExampleApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Logdash logdash;

    @Test
    void should_start_application_context() {
        assertThat(logdash).isNotNull();
        assertThat(logdash.logger()).isNotNull();
        assertThat(logdash.metrics()).isNotNull();
    }

    @Test
    void should_get_all_pets() {
        var response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/pets",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Burek", "Mruczek", "Olek");
    }

    @Test
    void should_create_random_pet() {
        var response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/pets/random",
                null,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
    }

    @Test
    void should_expose_actuator_health_endpoint() {
        var response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

}
