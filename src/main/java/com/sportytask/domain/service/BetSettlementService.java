package com.sportytask.domain.service;

import com.sportytask.domain.entities.*;
import com.sportytask.domain.repository.BetRepository;
import com.sportytask.domain.repository.BetSettlementRepository;
import com.sportytask.infrastructure.producer.BetSettlementProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BetSettlementService {

    private final BetRepository betRepository;
    private final BetSettlementRepository betSettlementRepository;
    private final BetSettlementProducer betSettlementProducer;

    /**
     * Process event outcome and identify bets to settle
     * This method is called when an event outcome is received from Kafka
     */
    @Transactional(readOnly = true)
    public void processEventOutcome(Event event) {
        log.info("Processing event outcome: eventId={}, eventName={}, winnerId={}",
                event.eventId(), event.eventName(), event.eventWinnerId());

        // Find all pending bets for this event
        List<Bet> pendingBets = betRepository.findByEventIdAndStatus(
                event.eventId(), BetStatus.PENDING);

        if (pendingBets.isEmpty()) {
            log.info("No pending bets found for event: {}", event.eventId());
            return;
        }

        log.info("Found {} pending bets for event: {}. Processing settlements...",
                pendingBets.size(), event.eventId());

        // Process each bet and send to RocketMQ
        pendingBets.forEach(bet -> settleBet(bet, event));
    }

    /**
     * Determine bet outcome and send to RocketMQ for settlement
     */
    private void settleBet(Bet bet, Event event) {
        boolean isWinner = bet.isWinningBet(event.eventWinnerId());

        BetSettlement settlement = BetSettlement.builder()
                .betId(bet.getBetId())
                .userId(bet.getUserId())
                .eventId(bet.getEventId())
                .eventWinnerId(event.eventWinnerId())
                .betAmount(bet.getBetAmount())
                .settlementAmount(calculatePayout(bet, isWinner))
                .status(isWinner ? SettlementStatus.WON : SettlementStatus.LOST)
                .settledAt(LocalDateTime.now())
                .build();

        // Send to RocketMQ for settlement processing
        betSettlementProducer.sendSettlement(settlement);

        log.info("Bet settlement sent to RocketMQ: betId={}, status={}, userBetOn={}, actualWinner={}",
                bet.getBetId(), settlement.getStatus(), bet.getEventWinnerId(), event.eventWinnerId());
    }

    /**
     * Calculate payout amount
     * For winners: bet amount * odds (simplified: 2x for this example)
     * For losers: 0
     */
    private BigDecimal calculatePayout(Bet bet, boolean isWinner) {
        if (isWinner) {
            // In real scenario, you'd multiply by odds from eventMarketId
            // For this example, we use a simple 2x multiplier
            return bet.getBetAmount().multiply(BigDecimal.valueOf(2));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Process the settlement (called by RocketMQ consumer)
     * This updates the bet status in the database and saves the settlement record
     */
    @Transactional
    public void processSettlement(BetSettlement settlement) {
        log.info("Processing settlement: betId={}, status={}, settlementAmount={}",
                settlement.getBetId(), settlement.getStatus(), settlement.getSettlementAmount());

        try {
            // Update bet status
            Bet bet = betRepository.findById(settlement.getBetId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Bet not found: " + settlement.getBetId()));

            BetStatus newStatus = settlement.getStatus() == SettlementStatus.WON
                    ? BetStatus.WON : BetStatus.LOST;

            bet.settle(newStatus);
            betRepository.save(bet);

            // Save settlement record
            betSettlementRepository.save(settlement);

            log.info("Settlement completed successfully: betId={}, finalStatus={}, amount={}",
                    bet.getBetId(), bet.getStatus(), settlement.getSettlementAmount());

        } catch (Exception e) {
            log.error("Failed to process settlement: betId={}, error={}",
                    settlement.getBetId(), e.getMessage(), e);
            throw e;
        }
    }
}

