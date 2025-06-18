package io.logdash.example.service;

import io.logdash.sdk.Logdash;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MonitoringService {

    private final Logdash logdash;
    private final PetService petService;
    private int monitoringCycles = 0;

    public MonitoringService(Logdash logdash, PetService petService) {
        this.logdash = logdash;
        this.petService = petService;
    }

    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void recordSystemMetrics() {
        var logger = logdash.logger();
        var metrics = logdash.metrics();

        monitoringCycles++;

        // JVM metrics
        var memoryBean = ManagementFactory.getMemoryMXBean();
        var heapUsage = memoryBean.getHeapMemoryUsage();

        var heapUsedMB = heapUsage.getUsed() / (1024 * 1024);

        // Simulated application metrics
        var activeUsers = ThreadLocalRandom.current().nextInt(50, 300);
        var errorRate = ThreadLocalRandom.current().nextDouble(0.1, 8.0);

        logger.verbose("System monitoring cycle", Map.of(
                "cycle", monitoringCycles,
                "activeUsers", activeUsers
        ));

        // Send metrics to Logdash
        metrics.set("jvm.memory.heap.used_mb", heapUsedMB);
        metrics.set("app.users.active", activeUsers);

        // Business metrics
        var petCount = petService.getTotalCount();
        metrics.set("pets.total.count", petCount);

        // Increment counters
        metrics.mutate("monitoring.cycles.total", 1);

        // Simulate random events
        if (ThreadLocalRandom.current().nextDouble() < 0.3) { // 30% chance
            logger.info("Random application event", Map.of(
                    "activeUsers", activeUsers
            ));
            metrics.mutate("app.events.user_activity", 1);
        }

        if (errorRate > 3.0) {
            logger.warn("High error rate detected", Map.of(
                    "errorRate", errorRate
            ));
            metrics.mutate("alerts.performance.slow_response", 1);
        }
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void simulateBusinessEvents() {
        var logger = logdash.logger();
        var metrics = logdash.metrics();

        // Simulate random pet creation
        if (ThreadLocalRandom.current().nextBoolean()) {
            var pet = petService.createRandomPet();

            logger.info(String.format("Background pet creation: petId=%s, name=%s, type=%s", pet.id(), pet.name(),
                    pet.type().name()));

            metrics.mutate("pets.created.background", 1);
            metrics.set("pets.total.count", petService.getTotalCount());
        }
    }
}
