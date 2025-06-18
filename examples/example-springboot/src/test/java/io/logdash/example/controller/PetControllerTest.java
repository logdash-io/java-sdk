package io.logdash.example.controller;

import io.logdash.example.domain.Pet;
import io.logdash.example.domain.PetType;
import io.logdash.example.service.PetService;
import io.logdash.sdk.log.LogdashLogger;
import io.logdash.sdk.metrics.LogdashMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PetController.class)
@TestPropertySource(properties = {
        "logdash.api-key=test-key",
        "logdash.enable-console-output=false"
})
class PetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PetService petService;

    @MockitoBean
    private LogdashLogger logger;

    @MockitoBean
    private LogdashMetrics metrics;

    @Test
    void should_get_all_pets() throws Exception {
        // Given
        var pets = List.of(
                new Pet(1L, "Burek", PetType.DOG),
                new Pet(2L, "Mruczek", PetType.CAT)
        );
        given(petService.getAllPets()).willReturn(pets);

        // When & Then
        mockMvc.perform(get("/api/pets"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Burek"))
                .andExpect(jsonPath("$[1].name").value("Mruczek"));

        // Verify logging and metrics
        verify(logger).info(eq("Retrieved all pets"), anyMap());
        verify(metrics).mutate("api.requests.total", 1);
        verify(metrics).mutate("api.pets.list.requests", 1);
        verify(metrics).set("pets.total.count", 2);
    }

    @Test
    void should_get_pet_by_id() throws Exception {
        // Given
        var pet = new Pet(1L, "Burek", PetType.DOG);
        given(petService.findById(1L)).willReturn(Optional.of(pet));

        // When & Then
        mockMvc.perform(get("/api/pets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Burek"))
                .andExpect(jsonPath("$.type").value("DOG"));

        // Verify logging and metrics
        verify(logger).debug(eq("Fetching pet with id: {}"), anyMap());
        verify(logger).info(eq("Pet found"), anyMap());
        verify(metrics).mutate("api.pets.found", 1);
    }

    @Test
    void should_return_not_found_for_non_existent_pet() throws Exception {
        // Given
        given(petService.findById(999L)).willReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/pets/999"))
                .andExpect(status().isNotFound());

        // Verify logging and metrics
        verify(logger).debug(eq("Fetching pet with id: {}"), anyMap());
        verify(logger).warn(eq("Pet not found"), anyMap());
        verify(metrics).mutate("api.pets.not_found", 1);
    }

    @Test
    void should_create_random_pet() throws Exception {
        // Given
        var pet = new Pet(3L, "Lucky", PetType.DOG);
        given(petService.createRandomPet()).willReturn(pet);

        // When & Then
        mockMvc.perform(post("/api/pets/random"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Lucky"))
                .andExpect(jsonPath("$.type").value("DOG"));

        // Verify logging and metrics
        verify(logger).info(eq("Created random pet"), anyMap());
        verify(metrics).mutate("api.pets.created", 1);
        verify(metrics).mutate("api.pets.random.created", 1);
    }

    @Test
    void should_create_pet_with_valid_request() throws Exception {
        // Given
        var pet = new Pet(4L, "Bella", PetType.CAT);
        given(petService.addPet("Bella", PetType.CAT)).willReturn(pet);

        // When & Then
        mockMvc.perform(post("/api/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Bella",
                                    "type": "CAT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.name").value("Bella"))
                .andExpect(jsonPath("$.type").value("CAT"));

        // Verify logging and metrics
        verify(logger).info(eq("Created pet"), anyMap());
        verify(metrics).mutate("api.pets.created", 1);
        verify(metrics).mutate("api.pets.manual.created", 1);
    }

    @Test
    void should_handle_delete_pet() throws Exception {
        // Given
        given(petService.deletePet(1L)).willReturn(true);

        // When & Then
        mockMvc.perform(delete("/api/pets/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void should_handle_delete_non_existent_pet() throws Exception {
        // Given
        given(petService.deletePet(999L)).willReturn(false);

        // When & Then
        mockMvc.perform(delete("/api/pets/999"))
                .andExpect(status().isNotFound());
    }
}
