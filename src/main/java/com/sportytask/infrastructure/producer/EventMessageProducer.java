package com.sportytask.infrastructure.producer;

import com.sportytask.domain.entities.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventMessageProducer {

    private static final String TOPIC = "event-outcomes";

    private final KafkaTemplate<String, Event> kafkaTemplate;

    public void sendEvent(Event event) {
        log.info("Sending event to topic {}: {}", TOPIC, event);

        CompletableFuture<SendResult<String, Event>> future =
            kafkaTemplate.send(TOPIC, String.valueOf(event.eventId()), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Event sent successfully: eventId={}, offset={}",
                    event.eventId(), result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send event: eventId={}, error={}",
                    event.eventId(), ex.getMessage(), ex);
            }
        });
    }
}
