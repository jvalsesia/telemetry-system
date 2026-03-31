package com.antigravity.telemetry.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.util.Map;

@Service
public class TelemetryBffClient {

    private static final Logger log = LoggerFactory.getLogger(TelemetryBffClient.class);
    private final RestClient restClient;

    public TelemetryBffClient(RestClient.Builder restClientBuilder,
                              @org.springframework.beans.factory.annotation.Value("${telemetry-bff.url:http://localhost:8084}") String telemetryBffUrl) {
        this.restClient = restClientBuilder.baseUrl(telemetryBffUrl).build();
    }

    public void sendAlert(String deviceId, double hr, double spo2, String reason) {
        log.info("Sending alert to BFF for device {}", deviceId);
        
        try {
            restClient.post()
                    .uri("/v1/alerts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("deviceId", deviceId, "reason", reason, "hr", hr, "spo2", spo2))
                    .retrieve()
                    .toBodilessEntity();
                    
            log.info("Successfully sent alert to BFF for device {}.", deviceId);
        } catch (Exception e) {
            log.warn("Failed to send alert to BFF for device {}: {}", deviceId, e.getMessage());
        }
    }
}
