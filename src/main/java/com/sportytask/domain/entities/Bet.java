package com.sportytask.domain.entities;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Bet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bet_id")
    private Long betId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "event_market_id")
    private Long eventMarketId;

    @Column(name = "event_winner_id")
    private Long eventWinnerId;

    @Column(name = "bet_amount")
    private BigDecimal betAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BetStatus status = BetStatus.PENDING;

    @Column(name = "placed_at")
    private LocalDateTime placedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    /**
     * Business logic to check if this bet is a winning bet
     */
    public boolean isWinningBet(Long actualWinnerId) {
        return this.eventWinnerId.equals(actualWinnerId);
    }

    /**
     * Settle the bet with the given status
     */
    public void settle(BetStatus status) {
        this.status = status;
        this.settledAt = LocalDateTime.now();
    }
}
