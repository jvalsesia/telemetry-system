package com.antigravity.telemetry.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "pending_alerts")
public class PendingAlert {

    @Id
    private String eventId;
    private String deviceId;
    private double heartRate;
    private double spO2;
    private String reason;
    private Instant createdAt;

    public PendingAlert() {}

    public PendingAlert(String eventId, String deviceId, double heartRate, double spO2, String reason) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.heartRate = heartRate;
        this.spO2 = spO2;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public String getDeviceId() { return deviceId; }
    public double getHeartRate() { return heartRate; }
    public double getSpO2() { return spO2; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
