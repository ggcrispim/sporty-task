package com.sportytask.domain.repository;

import com.sportytask.domain.entities.Bet;
import com.sportytask.domain.entities.BetStatus;
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
@DisplayName("BetRepository Tests")
class BetRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BetRepository betRepository;

    private Bet pendingBet1;
    private Bet settledBet;

    @BeforeEach
    void setUp() {
        System.out.println("Setting up test data...");
        pendingBet1 = Bet.builder()
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

        settledBet = Bet.builder()
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
        betRepository.deleteAll();
        entityManager.persistAndFlush(pendingBet1);
        entityManager.persistAndFlush(pendingBet2);
        entityManager.persistAndFlush(settledBet);
        entityManager.persistAndFlush(differentEventBet);
    }

    @Test
    @DisplayName("Should find pending bets by event ID")
    void shouldFindPendingBetsByEventId() {
        List<Bet> pendingBets = betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING);

        assertThat(pendingBets).hasSize(2);
        assertThat(pendingBets).extracting(Bet::getUserId)
                .containsExactlyInAnyOrder(1001L, 1002L);
    }

    @Test
    @DisplayName("Should not return settled bets when querying for pending")
    void shouldNotReturnSettledBetsWhenQueryingForPending() {
        List<Bet> pendingBets = betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING);

        assertThat(pendingBets).hasSize(2);
        assertThat(pendingBets).extracting(Bet::getStatus)
                .containsOnly(BetStatus.PENDING);
        assertThat(pendingBets).extracting(Bet::getBetId)
                .doesNotContain(settledBet.getBetId());
    }

    @Test
    @DisplayName("Should find bets for different event IDs separately")
    void shouldFindBetsForDifferentEventIdsSeparately() {
        List<Bet> betsEvent100 = betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING);
        List<Bet> betsEvent101 = betRepository.findByEventIdAndStatus(101L, BetStatus.PENDING);

        assertThat(betsEvent100).hasSize(2);
        assertThat(betsEvent101).hasSize(1);
        assertThat(betsEvent101.get(0).getUserId()).isEqualTo(1004L);
    }

    @Test
    @DisplayName("Should return empty list when no pending bets exist for event")
    void shouldReturnEmptyListWhenNoPendingBetsExist() {
        List<Bet> pendingBets = betRepository.findByEventIdAndStatus(999L, BetStatus.PENDING);

        assertThat(pendingBets).isEmpty();
    }

    @Test
    @DisplayName("Should find WON bets by event ID and status")
    void shouldFindWonBetsByEventIdAndStatus() {
        List<Bet> wonBets = betRepository.findByEventIdAndStatus(100L, BetStatus.WON);

        assertThat(wonBets).hasSize(1);
        assertThat(wonBets.get(0).getUserId()).isEqualTo(1003L);
    }

    @Test
    @DisplayName("Should save and retrieve bet correctly")
    void shouldSaveAndRetrieveBetCorrectly() {
        Bet newBet = Bet.builder()
                .userId(1005L)
                .eventId(102L)
                .eventMarketId(5004L)
                .eventWinnerId(401L)
                .betAmount(BigDecimal.valueOf(150.00))
                .status(BetStatus.PENDING)
                .placedAt(LocalDateTime.now())
                .build();

        Bet savedBet = betRepository.save(newBet);
        entityManager.flush();
        entityManager.clear();
        Bet retrievedBet = betRepository.findById(savedBet.getBetId()).orElse(null);

        assertThat(retrievedBet).isNotNull();
        assertThat(retrievedBet.getUserId()).isEqualTo(1005L);
        assertThat(retrievedBet.getEventId()).isEqualTo(102L);
        assertThat(retrievedBet.getBetAmount()).isEqualByComparingTo(BigDecimal.valueOf(150.00));
        assertThat(retrievedBet.getStatus()).isEqualTo(BetStatus.PENDING);
    }

    @Test
    @DisplayName("Should update bet status correctly")
    void shouldUpdateBetStatusCorrectly() {
        Bet bet = betRepository.findById(pendingBet1.getBetId()).orElseThrow();
        assertThat(bet.getStatus()).isEqualTo(BetStatus.PENDING);

        bet.settle(BetStatus.WON);
        betRepository.save(bet);
        entityManager.flush();
        entityManager.clear();

        Bet updatedBet = betRepository.findById(pendingBet1.getBetId()).orElseThrow();
        assertThat(updatedBet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(updatedBet.getSettledAt()).isNotNull();
    }

    @Test
    @DisplayName("Should count pending bets by event ID")
    void shouldCountPendingBetsByEventId() {
        long count = betRepository.countByEventIdAndStatus(100L, BetStatus.PENDING);

        assertThat(count).isEqualTo(2);
    }
}

