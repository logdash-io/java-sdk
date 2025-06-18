package io.logdash.example.service;

import io.logdash.example.domain.Pet;
import io.logdash.example.domain.PetType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PetService {

    private final Map<Long, Pet> pets = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public PetService() {
        addPet("Burek", PetType.DOG);
        addPet("Mruczek", PetType.CAT);
        addPet("Olek", PetType.BIRD);
    }

    public Pet addPet(String name, PetType type) {
        var pet = new Pet(idGenerator.getAndIncrement(), name, type);
        pets.put(pet.id(), pet);
        return pet;
    }

    public List<Pet> getAllPets() {
        return new ArrayList<>(pets.values());
    }

    public Optional<Pet> findById(Long id) {
        return Optional.ofNullable(pets.get(id));
    }

    public Pet createRandomPet() {
        var names = List.of("Azor", "Bella", "Charlie", "Luna", "Max", "Molly", "Oscar");
        var types = PetType.values();

        var randomName = names.get(ThreadLocalRandom.current().nextInt(names.size()));
        var randomType = types[ThreadLocalRandom.current().nextInt(types.length)];

        return addPet(randomName, randomType);
    }

    public int getTotalCount() {
        return pets.size();
    }

    public boolean deletePet(Long id) {
        return pets.remove(id) != null;
    }
}
