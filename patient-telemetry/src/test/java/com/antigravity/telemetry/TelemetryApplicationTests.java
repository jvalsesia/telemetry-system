package com.antigravity.telemetry;

import com.antigravity.telemetry.domain.PendingAlert;
import com.antigravity.telemetry.repository.PendingAlertRepository;
import com.antigravity.telemetry.schema.PatientTelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.model.ToxicDirection;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class TelemetryApplicationTests {

    static Network network = Network.newNetwork();
    static DockerImageName TOXIPROXY_IMAGE = DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.5.0");
    static DockerImageName KAFKA_IMAGE = DockerImageName.parse("confluentinc/cp-kafka:7.5.0");

    @Container
    static ToxiproxyContainer toxiproxy = new ToxiproxyContainer(TOXIPROXY_IMAGE).withNetwork(network);

    @Container
    static KafkaContainer kafka = new KafkaContainer(KAFKA_IMAGE).withKraft();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withNetwork(network)
            .withNetworkAliases("redis");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Map Redis proxy to our spring properties implicitly resolving through ToxiProxy port mapped to host
        registry.add("spring.data.redis.host", toxiproxy::getHost);
        registry.add("spring.data.redis.port", () -> toxiproxy.getMappedPort(8666));
        
        // Mock Schema Registry for tests so we don't bring up the entire Confluent Suite here
        registry.add("spring.kafka.consumer.properties.schema.registry.url", () -> "mock://noop");
        registry.add("spring.kafka.producer.properties.schema.registry.url", () -> "mock://noop");
        registry.add("spring.kafka.producer.key-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer", () -> "io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer");
    }

    @Autowired
    private KafkaTemplate<String, PatientTelemetryEvent> kafkaTemplate;

    @Autowired
    private PendingAlertRepository pendingAlertRepository;

    @BeforeEach
    void setUp() {
        pendingAlertRepository.deleteAll();
    }

    @Test
    void testAnomalyDetectionAndFallbackWithChaos() throws Exception {
        String deviceId = "DEVICE-999";

        // 1. CHOOSE YOUR TOXIC -> Setup Chaos Proxy for Redis (Inject Latency to test resilience)
        ToxiproxyClient toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
        Proxy redisProxy = toxiproxyClient.createProxy("redis", "0.0.0.0:8666", "redis:6379");
        redisProxy.toxics().latency("latency_toxic", ToxicDirection.DOWNSTREAM, 1000); // 1.0 second latency injection

        // 2. We simulate the external Paging API failure implicitly because the Dummy API is NOT defined in Testcontainers!
        // Resilience4j Circuit Breaker WILL fail and aggressively route the alert to the Database Fallback.

        // Send 3 critical anomaly events spanning the tumbling window (threshold = 3)
        for (int i = 0; i < 3; i++) {
            PatientTelemetryEvent event = PatientTelemetryEvent.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setDeviceId(deviceId)
                    .setHeartRate(150.0) // Metric > 120 (Critical)
                    .setSpO2(88.0)       // Metric < 90 (Critical)
                    .setTimestamp(System.currentTimeMillis())
                    .setDeviceError(false)
                    .build();

            kafkaTemplate.send("telemetry.events", deviceId, event);
        }

        // 3. Assert that alert fell back to the Database Table fully preserved, despite Redis chaotic latency
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<PendingAlert> alerts = pendingAlertRepository.findAll();
            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getDeviceId()).isEqualTo(deviceId);
            assertThat(alerts.get(0).getReason()).contains("Continuous Critical Vitals");
        });
        
        // Reset our toxins after the successful test
        redisProxy.toxics().get("latency_toxic").remove();
    }
}
