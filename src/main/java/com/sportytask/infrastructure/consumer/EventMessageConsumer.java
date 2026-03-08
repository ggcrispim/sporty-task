package com.sportytask.infrastructure.consumer;

import com.sportytask.domain.entities.Event;
import com.sportytask.domain.service.BetSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventMessageConsumer {

    private static final String TOPIC = "event-outcomes";
    private static final String GROUP_ID = "sporty-group";

    private final BetSettlementService betSettlementService;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void consumeEvent(@Payload Event event,
                            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Received event from Kafka topic {}: eventId={}, eventName={}, winnerId={}, partition={}, offset={}",
                TOPIC, event.eventId(), event.eventName(), event.eventWinnerId(), partition, offset);

        // Process the event and settle bets
        betSettlementService.processEventOutcome(event);
    }
}
