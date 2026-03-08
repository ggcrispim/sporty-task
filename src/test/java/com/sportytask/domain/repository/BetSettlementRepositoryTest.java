package com.sportytask.domain.repository;

import com.sportytask.domain.entities.Bet;
import com.sportytask.domain.entities.BetSettlement;
import com.sportytask.domain.entities.BetStatus;
import com.sportytask.domain.entities.SettlementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("BetSettlementRepository Tests")
class BetSettlementRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BetSettlementRepository betSettlementRepository;

    private BetSettlement wonSettlement;
    private BetSettlement lostSettlement;
    private BetSettlement differentEventSettlement;

    @BeforeEach
    void setUp() {
        Bet pendingBet1 = Bet.builder()
                .userId(1001L)
                .eventId(100L)
                .eventMarketId(5001L)
                .eventWinnerId(201L)
                .betAmount(BigDecimal.valueOf(50.00))
                .status(BetStatus.PENDING)
                .placedAt(LocalDateTime.now())
                .build();

        Bet pendingBet2 = Bet.builder()
                .userId(1002L)
                .eventId(100L)
                .eventMarketId(5001L)
                .eventWinnerId(202L)
                .betAmount(BigDecimal.valueOf(100.00))
                .status(BetStatus.PENDING)
                .placedAt(LocalDateTime.now())
                .build();

        Bet settledBet = Bet.builder()
                .userId(1003L)
                .eventId(100L)
                .eventMarketId(5002L)
                .eventWinnerId(201L)
                .betAmount(BigDecimal.valueOf(75.00))
                .status(BetStatus.WON)
                .placedAt(LocalDateTime.now())
                .settledAt(LocalDateTime.now())
                .build();

        Bet differentEventBet = Bet.builder()
                .userId(1004L)
                .eventId(101L)
                .eventMarketId(5003L)
                .eventWinnerId(301L)
                .betAmount(BigDecimal.valueOf(200.00))
                .status(BetStatus.PENDING)
                .placedAt(LocalDateTime.now())
                .build();

        wonSettlement = BetSettlement.builder()
                .betId(1L)
                .userId(1001L)
                .eventId(100L)
                .eventWinnerId(201L)
                .betAmount(BigDecimal.valueOf(50.00))
                .settlementAmount(BigDecimal.valueOf(100.00))
                .status(SettlementStatus.WON)
                .settledAt(LocalDateTime.now())
                .build();

        lostSettlement = BetSettlement.builder()
                .betId(2L)
                .userId(1002L)
                .eventId(100L)
                .eventWinnerId(201L)
                .betAmount(BigDecimal.valueOf(100.00))
                .settlementAmount(BigDecimal.ZERO)
                .status(SettlementStatus.LOST)
                .settledAt(LocalDateTime.now())
                .build();

        differentEventSettlement = BetSettlement.builder()
                .betId(3L)
                .userId(1003L)
                .eventId(101L)
                .eventWinnerId(302L)
                .betAmount(BigDecimal.valueOf(200.00))
                .settlementAmount(BigDecimal.valueOf(400.00))
                .status(SettlementStatus.WON)
                .settledAt(LocalDateTime.now())
                .build();

        entityManager.persistAndFlush(pendingBet1);
        entityManager.persistAndFlush(pendingBet2);
        entityManager.persistAndFlush(settledBet);
        entityManager.persistAndFlush(differentEventBet);

        entityManager.persistAndFlush(wonSettlement);
        entityManager.persistAndFlush(lostSettlement);
        entityManager.persistAndFlush(differentEventSettlement);
    }

    @Test
    @DisplayName("Should find settlements by event ID")
    void shouldFindSettlementsByEventId() {
        List<BetSettlement> settlements = betSettlementRepository.findByEventId(100L);

        assertThat(settlements).hasSize(2);
        assertThat(settlements).extracting(BetSettlement::getEventId)
                .containsOnly(100L);
    }
}

