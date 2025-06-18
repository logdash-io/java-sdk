package io.logdash.example.domain;

public record Pet(
        Long id,
        String name,
        PetType type
) {
    public Pet {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Pet name is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Pet type is required");
        }
    }
}
