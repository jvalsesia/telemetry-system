package com.antigravity.telemetry.kafka;

import com.antigravity.telemetry.schema.PatientTelemetryEvent;
import com.antigravity.telemetry.service.AnomalyDetectionService;
import com.antigravity.telemetry.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TelemetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumer.class);
    
    private final IdempotencyService idempotencyService;
    private final AnomalyDetectionService anomalyDetectionService;

    public TelemetryConsumer(IdempotencyService idempotencyService, AnomalyDetectionService anomalyDetectionService) {
        this.idempotencyService = idempotencyService;
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @KafkaListener(topics = "telemetry.events", groupId = "telemetry-pipeline-group")
    public void consume(PatientTelemetryEvent event) {
        String eventId = event.getEventId();
        
        log.debug("Received event: {}", eventId);
        
        // 1. Idempotency Check with Redis Caching
        if (!idempotencyService.isNewEvent(eventId)) {
            log.info("Duplicate event skipped processing idempotently: {}", eventId);
            return;
        }

        // 2. Evaluation
        anomalyDetectionService.evaluate(event);
    }
}
