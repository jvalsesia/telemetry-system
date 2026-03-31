package com.antigravity.bff.controller;

import com.antigravity.bff.domain.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

@RestController
@CrossOrigin(origins = "*") // Allow frontend apps to connect easily
public class SseAlertController {

    private static final Logger log = LoggerFactory.getLogger(SseAlertController.class);

    // Multicast sink allows multiple subscribers (frontend apps) to receive the same events
    private final Sinks.Many<Alert> alertSink = Sinks.many().multicast().directBestEffort();

    @PostMapping("/v1/alerts")
    public void receiveAlert(@RequestBody Alert alert) {
        log.info("BFF Received Alert for device {}: {}", alert.getDeviceId(), alert.getReason());
        // Emit the alert to all connected SSE clients
        Sinks.EmitResult result = alertSink.tryEmitNext(alert);
        if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn("Failed to emit alert to SSE stream: {}", result);
        }
    }

    @GetMapping(path = "/stream/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Alert>> streamAlerts() {
        log.info("New client subscribed to SSE stream");
        
        // Keep alive mechanism: send a ping every 15 seconds to keep connection open
        Flux<ServerSentEvent<Alert>> keepAliveFlux = Flux.interval(Duration.ofSeconds(15))
                .map(sequence -> ServerSentEvent.<Alert>builder()
                        .event("ping")
                        .data(new Alert("keep-alive", "ping", 0, 0))
                        .build());

        Flux<ServerSentEvent<Alert>> alertFlux = alertSink.asFlux()
                .map(alert -> ServerSentEvent.<Alert>builder()
                        .event("alert")
                        .data(alert)
                        .build());

        return Flux.merge(keepAliveFlux, alertFlux)
                .doOnCancel(() -> log.info("Client disconnected from SSE stream"));
    }
}
