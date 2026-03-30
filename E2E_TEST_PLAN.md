# End-to-End (E2E) Verification Plan

This End-to-End Test Plan outlines the testing strategy for the complete telemetry pipeline across its three core microservice solutions:
1. **Device Simulator** (`device-simulator`): Generates biometric payloads at scale and pushes them to Kafka.
2. **Patient Telemetry** (`patient-telemetry`): Consumes payloads, processes stateful anomaly detection, validates idempotency in Redis, and pages alerts.
3. **Paging API Simulator** (`paging-api-simulator`): A mock external service that receives HTTP alert webhooks.

---

## 🏗️ 1. Environment Startup

Before executing any tests, ensure all prerequisite infrastructure components (Kafka, Schema Registry, PostgreSQL, Redis) and the three core container solutions are properly launched in a dockerized stack.

```bash
docker compose up -d --build
```

**Pre-Flight Verification:**
- Verify `patient-telemetry` successfully connects to the Kafka brokers.
- Verify `device-simulator` successfully launches its Spring Boot application on port 8083.
- Verify `paging-api-simulator` is healthy and listening on port 8082.

---

## 🧪 2. Test Scenarios (The 3 Core Solutions)

### Scenario A: Initialization & Ingestion (Device Simulator)
**Objective:** Confirm the `device-simulator` solution is correctly injecting the generated data into the ecosystem.

1. **Trigger:** The Device Simulator service does not start generating streams automatically upon load; it exposes an initialization REST controller. Send a `POST` request to start the simulation:
   ```bash
   curl -X POST http://localhost:8083/api/v1/simulator/start
   ```
2. **Assertion:** Monitor the logs to verify data emission starts utilizing virtual threads without schema serialization errors:
   ```bash
   docker logs -f device-simulator
   ```

### Scenario B: Core Routing & Filtering (Patient Telemetry)
**Objective:** Confirm `patient-telemetry` captures the stream, maintains deduplication integrity (Idempotency), and isolates actual biometric anomalies.

1. **Trigger:** With the Device Simulator now running, the Patient Telemetry core logic should automatically begin consuming events from the `telemetry.events` Kafka topic.
2. **Assertion:** Monitor the `patient-telemetry` container logs.
   ```bash
   docker logs -f patient-telemetry
   ```
   - *Expected Behavior 1:* You should begin seeing log messages confirming standard vitals are ingested.
   - *Expected Behavior 2 (Anomalies):* For devices flagged to deteriorate, you will see alerts generated: `Sending alert to external Paging API for device SIM-DEV-XXXX`.
   - *Expected Behavior 3 (Idempotency):* If identical `EventIds` hit the system (which can be manually mocked via `kafka-console-producer`), Redis should catch them, and you should see an `Event Dropped` warning.

### Scenario C: Delivery Validation (Paging API Simulator)
**Objective:** Confirm the end-of-the-line destination securely receives the notification webhook. 

1. **Trigger:** The Patient Telemetry system pages out to the external simulator via REST.
2. **Assertion:** Tail the `paging-api-simulator` logs.
   ```bash
   docker logs -f paging-api-mock
   ```
   - *Expected Behavior:* You should see incoming `POST` requests bearing the alert payload (containing the critical `deviceId`, `heartRate`, `spO2`, etc.) mirroring the telemetry logs in Scenario B.

---

## 🚑 3. Resilience & Disaster Recovery

The system utilizes `Resilience4j` and PostgreSQL as a circuit-breaking fallback mechanism if the Mock Paging API is down.

1. **Simulate Outage:** Forcefully shutdown the destination solution.
   ```bash
   docker stop paging-api-mock
   ```
2. **Verify Circuit Breaker (`patient-telemetry`):** The Patient Telemetry logs will indicate `Connection Refused` followed by a `Circuit Breaker triggered. Attempting Fallback to DB...` warning.
3. **Verify Database:** Confirm the failed payloads are securely stored inside Postgres.
   ```bash
   docker exec -it postgres psql -U telemetry_user -d telemetry_db -c "SELECT * FROM pending_alerts;"
   ```
4. **Resolution:** Restart the application (`docker start paging-api-mock`). Ensure that after the open state window passes, `patient-telemetry` reconnects and resumes paging the API safely.
