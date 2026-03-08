package com.sportytask.infrastructure.consumer;

import com.sportytask.domain.entities.BetSettlement;
import com.sportytask.domain.service.BetSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "bet-settlements",
        consumerGroup = "bet-settlements-consumer-group",
        nameServer = "${rocketmq.name-server}"
)
public class BetSettlementConsumer implements RocketMQListener<String> {

    private final BetSettlementService betSettlementService;
    private final ObjectMapper objectMapper;

    /**
     * Consume bet settlement messages from RocketMQ
     * This method processes the settlement and updates the database
     */
    @Override
    public void onMessage(String message) {
        log.info("Received message from RocketMQ: {}", message);

        try {
            BetSettlement settlement = objectMapper.readValue(message, BetSettlement.class);

            log.info("Parsed settlement: betId={}, eventId={}, status={}",
                    settlement.getBetId(), settlement.getEventId(), settlement.getStatus());

            betSettlementService.processSettlement(settlement);
            log.info("Settlement processed successfully: betId={}, status={}",
                    settlement.getBetId(), settlement.getStatus());
        } catch (Exception e) {
            log.error("Failed to process settlement from RocketMQ: error={}",
                    e.getMessage(), e);
            // In production: implement retry logic or dead letter queue
            throw new RuntimeException("Failed to process settlement", e);
        }
    }
}

