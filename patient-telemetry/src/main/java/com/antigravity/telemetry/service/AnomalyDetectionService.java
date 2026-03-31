package com.antigravity.telemetry.service;

import com.antigravity.telemetry.client.PagingApiClient;
import com.antigravity.telemetry.schema.PatientTelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);
    
    private final StringRedisTemplate redisTemplate;
    private final PagingApiClient pagingApiClient;
    private final com.antigravity.telemetry.client.TelemetryBffClient telemetryBffClient;
    
    private static final int REQUIRED_CONSECUTIVE_ANOMALIES = 3;

    public AnomalyDetectionService(StringRedisTemplate redisTemplate, PagingApiClient pagingApiClient, com.antigravity.telemetry.client.TelemetryBffClient telemetryBffClient) {
        this.redisTemplate = redisTemplate;
        this.pagingApiClient = pagingApiClient;
        this.telemetryBffClient = telemetryBffClient;
    }

    public void evaluate(PatientTelemetryEvent event) {
        String deviceId = event.getDeviceId();
        double hr = event.getHeartRate();
        double spo2 = event.getSpO2();

        boolean isAnomalous = isAnomalousReading(hr, spo2);
        String redisKey = "telemetry:anomaly:count:" + deviceId;

        if (isAnomalous) {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            redisTemplate.expire(redisKey, Duration.ofMinutes(5)); // window duration
            log.debug("Current anomaly count for {} is {}", deviceId, count);
            
            if (count != null && count >= REQUIRED_CONSECUTIVE_ANOMALIES) {
                log.warn("Anomaly threshold breached! Device {} has had {} consequent critical readings.", deviceId, count);
                String reason = "Continuous Critical Vitals: HR=" + hr + ", SpO2=" + spo2;
                pagingApiClient.sendAlert(event.getEventId(), deviceId, hr, spo2, reason);
                telemetryBffClient.sendAlert(deviceId, hr, spo2, reason);
                // Flush counter to prevent infinite overlapping alerts
                redisTemplate.delete(redisKey);
            }
        } else {
            // Patient stabilized, reset anomalous counters
            redisTemplate.delete(redisKey);
        }
    }

    private boolean isAnomalousReading(double hr, double spo2) {
        return spo2 < 90.0 || hr > 120.0 || hr < 40.0;
    }
}
