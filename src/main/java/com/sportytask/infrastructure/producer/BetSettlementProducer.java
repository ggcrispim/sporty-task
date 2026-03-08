package com.sportytask.infrastructure.producer;

import com.sportytask.domain.entities.BetSettlement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class BetSettlementProducer {

    private static final String TOPIC = "bet-settlements";
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Send bet settlement message to RocketMQ
     */
    public void sendSettlement(BetSettlement settlement) {
        log.info("Sending settlement to RocketMQ topic {}: betId={}, status={}",
                TOPIC, settlement.getBetId(), settlement.getStatus());

        try {
            SendResult sendResult = rocketMQTemplate.syncSend(
                    TOPIC,
                    MessageBuilder.withPayload(objectMapper.writeValueAsString(settlement))
                            .setHeader("betId", settlement.getBetId().toString())
                            .setHeader("eventId", settlement.getEventId().toString())
                            .setHeader("userId", settlement.getUserId().toString())
                            .build()
            );

            log.info("Settlement sent successfully to RocketMQ: betId={}, messageId={}, status={}, queueId={}",
                    settlement.getBetId(),
                    sendResult.getMsgId(),
                    sendResult.getSendStatus(),
                    sendResult.getMessageQueue().getQueueId());

        }  catch (Exception e) {
            log.error("Failed to send settlement to RocketMQ: betId={}, error={}",
                    settlement.getBetId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send settlement message to RocketMQ", e);
        }
    }
}

