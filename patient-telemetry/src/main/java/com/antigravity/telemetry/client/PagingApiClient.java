package com.antigravity.telemetry.client;

import com.antigravity.telemetry.domain.PendingAlert;
import com.antigravity.telemetry.repository.PendingAlertRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.util.Map;

@Service
public class PagingApiClient {

    private static final Logger log = LoggerFactory.getLogger(PagingApiClient.class);
    private final RestClient restClient;
    private final PendingAlertRepository pendingAlertRepository;

    public PagingApiClient(PendingAlertRepository pendingAlertRepository, 
                           RestClient.Builder restClientBuilder,
                           @org.springframework.beans.factory.annotation.Value("${paging-api.url:http://localhost:8082}") String pagingApiUrl) {
        this.restClient = restClientBuilder.baseUrl(pagingApiUrl).build(); 
        this.pendingAlertRepository = pendingAlertRepository;
    }

    @CircuitBreaker(name = "pagingApi", fallbackMethod = "dbFallback")
    public void sendAlert(String eventId, String deviceId, double hr, double spo2, String reason) {
        log.info("Sending alert to external Paging API for device {} (eventId={})", deviceId, eventId);
        
        // Simulating the API request to the wiremock dummy service
        restClient.post()
                .uri("/v1/alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("deviceId", deviceId, "reason", reason, "hr", hr, "spo2", spo2))
                .retrieve()
                .toBodilessEntity();
                
        log.info("Successfully paged device {} through the API.", deviceId);
    }

    // Resilience4J fallback method logic
    public void dbFallback(String eventId, String deviceId, double hr, double spo2, String reason, Throwable throwable) {
        log.warn("Circuit Breaker triggered. Attempting Fallback to DB for eventId: {}. Reason: {}", eventId, throwable.getMessage());
        PendingAlert alert = new PendingAlert(eventId, deviceId, hr, spo2, reason);
        pendingAlertRepository.save(alert);
        log.info("Successfully fell back to database. Stored PendingAlert safely.");
    }
}
