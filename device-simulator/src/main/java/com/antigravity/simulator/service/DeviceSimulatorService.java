package com.antigravity.simulator.service;

import com.antigravity.telemetry.schema.PatientTelemetryEvent;
import jakarta.annotation.PreDestroy;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.BaggageInScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DeviceSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(DeviceSimulatorService.class);
    private static final String TOPIC = "telemetry.events";

    private final KafkaTemplate<String, PatientTelemetryEvent> kafkaTemplate;
    private final Tracer tracer;
    
    @Value("${simulator.num-devices}")
    private int numDevices;
    
    @Value("${simulator.tick-interval-ms}")
    private long tickIntervalMs;

    private ExecutorService executorService;
    private volatile boolean running = false;

    public DeviceSimulatorService(KafkaTemplate<String, PatientTelemetryEvent> kafkaTemplate, Tracer tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.tracer = tracer;
    }

    public synchronized void startSimulation() {
        if (running) {
            log.warn("Simulation is already running.");
            return;
        }
        running = true;
        log.info("Starting simulator with {} devices using Virtual Threads. Tick interval: {} ms", numDevices, tickIntervalMs);
        
        executorService = Executors.newVirtualThreadPerTaskExecutor();
        
        for (int i = 0; i < numDevices; i++) {
            final String deviceId = "SIM-DEV-" + String.format("%05d", i);
            executorService.submit(() -> simulateDevice(deviceId));
        }
    }
    
    @PreDestroy
    public synchronized void stopSimulation() {
        running = false;
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    private void simulateDevice(String deviceId) {
        // Initial clean state
        double currentHeartRate = 70.0 + ThreadLocalRandom.current().nextDouble() * 20.0;
        double currentSpO2 = 98.0 + ThreadLocalRandom.current().nextDouble() * 2.0;
        
        // Probability of this device being anomalous
        boolean isAnomalous = ThreadLocalRandom.current().nextDouble() < 0.05; // 5% of devices will deteriorate
        boolean errorState = false;

        // Introduce start jitter so not all 50k devices fire at once
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(0, tickIntervalMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        while (running) {
            try {
                // Random walk vitals
                currentHeartRate += (ThreadLocalRandom.current().nextDouble() - 0.5) * 5.0; // [-2.5, 2.5]
                currentSpO2 += (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.0; // [-0.5, 0.5]
                
                // If anomalous, push towards bad thresholds over time
                if (isAnomalous) {
                    currentHeartRate += 1.0; // steadily increasing HR
                    currentSpO2 -= 0.5;      // steadily decreasing SpO2
                }

                // Bounds checking
                currentHeartRate = Math.max(30.0, Math.min(220.0, currentHeartRate));
                currentSpO2 = Math.max(50.0, Math.min(100.0, currentSpO2));
                
                PatientTelemetryEvent event = PatientTelemetryEvent.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setDeviceId(deviceId)
                        .setTimestamp(System.currentTimeMillis())
                        .setHeartRate(currentHeartRate)
                        .setSpO2(currentSpO2)
                        .setDeviceError(errorState)
                        .build();

                try (BaggageInScope scope = this.tracer.createBaggageInScope("deviceId", deviceId)) {
                    kafkaTemplate.send(TOPIC, deviceId, event);
                }

                Thread.sleep(tickIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                log.error("Simulation error for device {}", deviceId, e);
            }
        }
    }
}
