# Product Requirements Document (PRD): Real-Time Patient Telemetry & Anomaly Detection Pipeline

## 1. Project Overview
A real-time, HIPAA-compliant pipeline designed to ingest continuous biometric data from hospital monitoring devices. The system will detect anomalies (e.g., critical drops in SpO2 or extreme heart rates) and trigger alerts to external paging systems reliably.

## 2. Core Constraints & Premises
- **HIPAA Compliance:** Zero Protected Health Information (PHI) in event payloads. Events strictly contain `device_id`, `timestamp`, and biometrics. Patient mapping occurs securely downstream only when triggering alerts.
- **Strict Ordering:** Events for a specific device must be processed sequentially. `device_id` is used as the Kafka partition key.
- **Schema Evolution & Serialization:** Protocol Buffers (Protobuf) deployed with Confluent Schema Registry. Highly compressed and strictly typed.
- **Idempotency:** Kafka "at-least-once" delivery mandates deduplication. A Redis cache (`SETNX` with TTL) will handle high-throughput deduplication to prevent database bottlenecks.
- **Technology Stack:**
  - **Messaging:** Kafka (KRaft mode) + Confluent Schema Registry
  - **Serialization:** Protobuf
  - **Backend:** Spring Boot (Web MVC) + Virtual Threads (`spring.threads.virtual.enabled=true`) on Java 21+
  - **Resilience:** Resilience4j for Circuit Breaking external API calls
  - **Datastore/Cache:** PostgreSQL + Redis
  - **Observability:** Micrometer Tracing + Zipkin
  - **Testing:** Testcontainers (Kafka, Redis, Postgres), Toxiproxy for Chaos Engineering

## 3. Resilience & Observability Patterns
- **Circuit Breaker:** Wrap calls to the Paging API in a Resilience4j circuit breaker to prevent consumer threads from hanging on timeouts.
- **Fallback Strategy (Pending DB Table):** If the external Paging API circuit opens during an anomaly, the alert will be dumped into a PostgreSQL `pending_alerts` table for asynchronous retry processes to guarantee message delivery later.
- **Dead Letter Queues (DLQ):** Messages failing deserialization or missing critical data go to a DLQ (`telemetry-dlq`) to unblock the partition.
- **Distributed Tracing:** Micrometer injects `traceId` into Kafka headers. End-to-end tracing spans from Kafka ingestion \u2192 Processing \u2192 Paging API call.
- **Chaos Engineering:** Use Toxiproxy to intentionally inject latency or sever connections during testing to prove the resilience of the system.

## 4. Finalized Application Details (Interview Results)
1. **Biometric Scope:** Specifically tracking `heart_rate` and `sp_o2` limitlessly.
2. **Anomaly Logic:** Stateful processing/windowing. The system will track anomalies occurring consistently over a duration before alerting (e.g., SpO2 < 90% for multiple continuous events).
3. **Scale & Throughput:** Target throughput set to **10,000 events/second**. This justifies utilizing the Redis cache deduplication path over the database constraint route to decrease Disk I/O.
4. **Paging API Simulation:** Using a *WireMock* container to independently act as a Dummy Paging API during load and chaos tests.

