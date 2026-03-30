# System Sequence Diagram

This diagram visualizes the end-to-end data flow between the microservices, databases, and message brokers within the telemetry ecosystem. It also maps the circuit-breaker resilience pathway when external services fail.

```mermaid
sequenceDiagram
    autonumber
    
    participant DS as Device Simulator
    participant SR as Schema Registry
    participant K as Kafka (telemetry.events)
    participant PT as Patient Telemetry
    participant R as Redis (Idempotency)
    participant API as Paging API (Mock)
    participant DB as PostgreSQL (Fallback)

    %% Scenario 1: Telemetry Generation & Ingestion
    Note over DS, K: 1. Biometric Ingestion Phase
    DS->>SR: Fetch/Register Protobuf Schema
    SR-->>DS: Schema ID
    DS-)K: Publish PatientTelemetryEvent (Protobuf)
    
    %% Scenario 2: Processing & Deduplication
    Note over K, PT: 2. Core Processing Phase
    K-->>PT: Consume PatientTelemetryEvent
    
    PT->>R: SETNX eventId (Deduplication Check)
    alt is duplicate event
        R-->>PT: false (Key Exists)
        PT->>PT: Drop Event
    else is new event
        R-->>PT: true (Key Stored)
        PT->>PT: Stateful Anomaly Detection 
    end
    
    %% Scenario 3: Paging & Resilience
    Note over PT, DB: 3. Alert Routing & Resilience Phase
    opt Anomaly Threshold Breached
        PT->>API: HTTP POST /v1/alerts
        
        alt API returns 200 OK
            API-->>PT: Successfully Paged
        else API Unreachable / Timeout
            PT--xAPI: Connection Refused / 500
            Note over PT: Resilience4j Circuit Breaker Trips!
            PT->>DB: Fallback: INSERT INTO pending_alerts
            DB-->>PT: Successfully Saved
        end
    end
```
