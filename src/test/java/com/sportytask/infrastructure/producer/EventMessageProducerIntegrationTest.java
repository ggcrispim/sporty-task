package com.sportytask.infrastructure.producer;

import com.sportytask.domain.entities.Event;
import com.sportytask.infrastructure.producer.BetSettlementProducer;
import com.sportytask.infrastructure.producer.EventMessageProducer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@EnableAutoConfiguration(exclude = {
        org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration.class
})
@DisplayName("EventMessageProducer Integration Tests")
class EventMessageProducerIntegrationTest {

    private static final String TOPIC = "event-outcomes";

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @Autowired
    private EventMessageProducer eventMessageProducer;

    // Mock RocketMQ beans since autoconfiguration is excluded
    @MockitoBean
    private RocketMQTemplate rocketMQTemplate;

    @MockitoBean
    private BetSettlementProducer betSettlementProducer;

    private KafkaConsumer<String, Event> consumer;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "test-group");
        registry.add("spring.kafka.producer.key-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer", () -> "org.springframework.kafka.support.serializer.JsonSerializer");
        // Disable RocketMQ auto-configuration for this test
        registry.add("rocketmq.name-server", () -> "localhost:9876");
        registry.add("rocketmq.producer.group", () -> "test-producer-group");
    }

    @BeforeEach
    void setUp() {
        // Create JsonDeserializer with proper type configuration
        JacksonJsonDeserializer<Event> eventDeserializer = new JacksonJsonDeserializer<>(Event.class);
        eventDeserializer.addTrustedPackages("*");
        eventDeserializer.setUseTypeMapperForKey(false);

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(consumerProps, new StringDeserializer(), eventDeserializer);
        consumer.subscribe(Collections.singletonList(TOPIC));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("Should send event to Kafka topic successfully")
    void shouldSendEventToKafkaTopicSuccessfully() {
        // Given
        Event event = new Event(100L, "Champions League Final", 201L);

        // When
        eventMessageProducer.sendEvent(event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, Event> records = consumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isGreaterThan(0);

            ConsumerRecord<String, Event> record = records.iterator().next();
            assertThat(record.key()).isEqualTo("100");
            assertThat(record.value().eventId()).isEqualTo(100L);
            assertThat(record.value().eventName()).isEqualTo("Champions League Final");
            assertThat(record.value().eventWinnerId()).isEqualTo(201L);
        });
    }

    @Test
    @DisplayName("Should use event ID as message key")
    void shouldUseEventIdAsMessageKey() {
        // Given
        Event event = new Event(999L, "Test Match", 301L);

        // When
        eventMessageProducer.sendEvent(event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, Event> records = consumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isGreaterThan(0);

            ConsumerRecord<String, Event> record = records.iterator().next();
            assertThat(record.key()).isEqualTo("999");
        });
    }

    @Test
    @DisplayName("Should send multiple events successfully")
    void shouldSendMultipleEventsSuccessfully() {
        // Given
        Event event1 = new Event(101L, "Match 1", 201L);
        Event event2 = new Event(102L, "Match 2", 202L);
        Event event3 = new Event(103L, "Match 3", 203L);

        // When
        eventMessageProducer.sendEvent(event1);
        eventMessageProducer.sendEvent(event2);
        eventMessageProducer.sendEvent(event3);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, Event> records = consumer.poll(Duration.ofMillis(1000));
            assertThat(records.count()).isGreaterThanOrEqualTo(3);
        });
    }

    @Test
    @DisplayName("Should send event to correct topic")
    void shouldSendEventToCorrectTopic() {
        // Given
        Event event = new Event(200L, "Premier League Match", 401L);

        // When
        eventMessageProducer.sendEvent(event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, Event> records = consumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isGreaterThan(0);

            ConsumerRecord<String, Event> record = records.iterator().next();
            assertThat(record.topic()).isEqualTo(TOPIC);
        });
    }

    @Test
    @DisplayName("Should preserve all event fields in message")
    void shouldPreserveAllEventFieldsInMessage() {
        // Given
        Long eventId = 12345L;
        String eventName = "World Cup Final 2026";
        Long winnerId = 98765L;
        Event event = new Event(eventId, eventName, winnerId);

        // When
        eventMessageProducer.sendEvent(event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, Event> records = consumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isGreaterThan(0);

            ConsumerRecord<String, Event> record = records.iterator().next();
            Event receivedEvent = record.value();

            assertThat(receivedEvent.eventId()).isEqualTo(eventId);
            assertThat(receivedEvent.eventName()).isEqualTo(eventName);
            assertThat(receivedEvent.eventWinnerId()).isEqualTo(winnerId);
        });
    }
}




