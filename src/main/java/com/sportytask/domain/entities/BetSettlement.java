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
@Table(name = "bet_settlement")
public class BetSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(name = "bet_id", unique = true, nullable = false)
    private Long betId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "event_winner_id", nullable = false)
    private Long eventWinnerId;

    @Column(name = "bet_amount", nullable = false)
    private BigDecimal betAmount;

    @Column(name = "settlement_amount", nullable = false)
    private BigDecimal settlementAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementStatus status;

    @Column(name = "settled_at", nullable = false)
    private LocalDateTime settledAt;
}

