package io.logdash.example.controller;

import io.logdash.example.domain.Pet;
import io.logdash.example.domain.PetType;
import io.logdash.example.service.PetService;
import io.logdash.sdk.Logdash;
import io.logdash.sdk.log.LogdashLogger;
import io.logdash.sdk.metrics.LogdashMetrics;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pets")
public class PetController {

    private final PetService petService;
    private final LogdashLogger logger;
    private final LogdashMetrics metrics;

    public PetController(PetService petService, Logdash logdash) {
        this.petService = petService;
        this.logger = logdash.logger();
        this.metrics = logdash.metrics();
    }

    @GetMapping
    public ResponseEntity<List<Pet>> getAllPets() {
        var pets = petService.getAllPets();

        logger.info("Retrieved all pets", Map.of(
                "count", pets.size(),
                "endpoint", "/api/pets",
                "timestamp", LocalDateTime.now()
        ));

        metrics.mutate("api.requests.total", 1);
        metrics.mutate("api.pets.list.requests", 1);
        metrics.set("pets.total.count", pets.size());

        return ResponseEntity.ok(pets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable Long id) {
        var pet = petService.findById(id);

        if (pet.isPresent()) {
            logger.debug("Pet retrieved successfully", Map.of(
                    "petId", id,
                    "name", pet.get().name(),
                    "type", pet.get().type().name()
            ));
            metrics.mutate("api.pets.get.success", 1);
            return ResponseEntity.ok(pet.get());
        } else {
            logger.warn("Pet not found", Map.of(
                    "petId", id
            ));
            metrics.mutate("api.pets.get.not_found", 1);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Pet> createPet(@RequestBody CreatePetRequest request) {
        try {
            var pet = petService.addPet(request.name(), request.type());

            logger.info("Pet created successfully", Map.of(
                    "petId", pet.id(),
                    "name", pet.name(),
                    "type", pet.type().name()
            ));

            metrics.mutate("pets.created.total", 1);
            metrics.mutate("api.pets.create.success", 1);
            metrics.set("pets.total.count", petService.getTotalCount());

            return ResponseEntity.ok(pet);

        } catch (IllegalArgumentException e) {
            logger.error("Pet creation failed", Map.of(
                    "error", e.getMessage(),
                    "requestName", request.name(),
                    "requestType", request.type()
            ));
            metrics.mutate("api.pets.create.validation_error", 1);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/random")
    public ResponseEntity<Pet> createRandomPet() {
        var pet = petService.createRandomPet();

        logger.info("Random pet created", Map.of(
                "petId", pet.id(),
                "name", pet.name(),
                "type", pet.type().name()
        ));

        metrics.mutate("pets.created.random", 1);
        metrics.mutate("api.requests.total", 1);
        metrics.set("pets.total.count", petService.getTotalCount());

        return ResponseEntity.ok(pet);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePet(@PathVariable Long id) {
        boolean deleted = petService.deletePet(id);

        if (deleted) {
            logger.info("Pet deleted successfully", Map.of(
                    "petId", id
            ));
            metrics.mutate("pets.deleted.total", 1);
            metrics.set("pets.total.count", petService.getTotalCount());
            return ResponseEntity.noContent().build();
        } else {
            logger.warn("Delete failed - pet not found", Map.of("petId", id));
            metrics.mutate("api.pets.delete.not_found", 1);
            return ResponseEntity.notFound().build();
        }
    }

    public record CreatePetRequest(String name, PetType type) {
    }
}
