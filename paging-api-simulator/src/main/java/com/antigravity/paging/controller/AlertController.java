package com.antigravity.paging.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/v1")
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);
    // Thread-safe toggle to dynamically switch the API into a failed outage state
    private final AtomicBoolean shouldFail = new AtomicBoolean(false);

    @PostMapping("/alerts")
    public ResponseEntity<String> receiveAlert(@RequestBody Map<String, Object> payload) {
        if (shouldFail.get()) {
            log.error("Artificial failure triggered by FailureToggle! Incoming alert rejected with HTTP 500.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Simulated API Outage");
        }
        
        log.info("\uD83D\uDEA8 RECEIVED CRITICAL HOSPITAL ALERT: {}", payload);
        return ResponseEntity.ok("Alert dispatched to paging network securely.");
    }

    @GetMapping("/config/fail")
    public String toggleFailure() {
        boolean currentState = shouldFail.get();
        shouldFail.set(!currentState);
        
        if (shouldFail.get()) {
            log.warn("=== PAGING API OUTAGE SIMULATION ACTIVATED ===");
            return "Failure Simulation is now ENABLED (Returning HTTP 500 ERRORS). Circuit Breaker should open!";
        } else {
            log.info("=== PAGING API RECOVERED ===");
            return "Failure Simulation is now DISABLED (Returning HTTP 200 OK). Circuit Breaker should close!";
        }
    }
}
