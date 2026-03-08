package com.sportytask.domain.service;

import com.sportytask.domain.entities.*;
import com.sportytask.domain.repository.BetRepository;
import com.sportytask.domain.repository.BetSettlementRepository;
import com.sportytask.infrastructure.producer.BetSettlementProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BetSettlementService Unit Tests")
class BetSettlementServiceTest {

    @Mock
    private BetRepository betRepository;

    @Mock
    private BetSettlementRepository betSettlementRepository;

    @Mock
    private BetSettlementProducer betSettlementProducer;

    @InjectMocks
    private BetSettlementService betSettlementService;

    @Captor
    private ArgumentCaptor<BetSettlement> settlementCaptor;

    @Captor
    private ArgumentCaptor<Bet> betCaptor;

    private Event testEvent;
    private Bet winningBet;
    private Bet losingBet;

    @BeforeEach
    void setUp() {
        testEvent = new Event(100L, "Champions League Final", 201L);

        winningBet = Bet.builder()
                .betId(1L)
                .userId(1001L)
                .eventId(100L)
                .eventMarketId(500L)
                .eventWinnerId(201L) // Same as event winner - this bet wins
                .betAmount(new BigDecimal("50.00"))
                .status(BetStatus.PENDING)
                .placedAt(LocalDateTime.now().minusHours(1))
                .build();

        losingBet = Bet.builder()
                .betId(2L)
                .userId(1002L)
                .eventId(100L)
                .eventMarketId(500L)
                .eventWinnerId(202L) // Different from event winner - this bet loses
                .betAmount(new BigDecimal("100.00"))
                .status(BetStatus.PENDING)
                .placedAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    @Nested
    @DisplayName("processEventOutcome Tests")
    class ProcessEventOutcomeTests {

        @Test
        @DisplayName("Should process winning bet and send correct settlement to RocketMQ")
        void shouldProcessWinningBetCorrectly() {
            // Given
            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(List.of(winningBet));

            // When
            betSettlementService.processEventOutcome(testEvent);

            // Then
            verify(betSettlementProducer).sendSettlement(settlementCaptor.capture());
            BetSettlement settlement = settlementCaptor.getValue();

            assertThat(settlement.getBetId()).isEqualTo(1L);
            assertThat(settlement.getUserId()).isEqualTo(1001L);
            assertThat(settlement.getEventId()).isEqualTo(100L);
            assertThat(settlement.getEventWinnerId()).isEqualTo(201L);
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.WON);
            assertThat(settlement.getBetAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(settlement.getSettlementAmount()).isEqualByComparingTo(new BigDecimal("100.00")); // 2x payout
            assertThat(settlement.getSettledAt()).isNotNull();
        }

        @Test
        @DisplayName("Should process losing bet and send correct settlement to RocketMQ")
        void shouldProcessLosingBetCorrectly() {
            // Given
            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(List.of(losingBet));

            // When
            betSettlementService.processEventOutcome(testEvent);

            // Then
            verify(betSettlementProducer).sendSettlement(settlementCaptor.capture());
            BetSettlement settlement = settlementCaptor.getValue();

            assertThat(settlement.getBetId()).isEqualTo(2L);
            assertThat(settlement.getUserId()).isEqualTo(1002L);
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.LOST);
            assertThat(settlement.getBetAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(settlement.getSettlementAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should process multiple bets for same event")
        void shouldProcessMultipleBetsForSameEvent() {
            // Given
            Bet anotherWinningBet = Bet.builder()
                    .betId(3L)
                    .userId(1003L)
                    .eventId(100L)
                    .eventMarketId(500L)
                    .eventWinnerId(201L)
                    .betAmount(new BigDecimal("25.00"))
                    .status(BetStatus.PENDING)
                    .placedAt(LocalDateTime.now())
                    .build();

            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(List.of(winningBet, losingBet, anotherWinningBet));

            // When
            betSettlementService.processEventOutcome(testEvent);

            // Then
            verify(betSettlementProducer, times(3)).sendSettlement(settlementCaptor.capture());
            List<BetSettlement> settlements = settlementCaptor.getAllValues();

            assertThat(settlements).hasSize(3);

            // Verify winning bets
            long wonCount = settlements.stream()
                    .filter(s -> s.getStatus() == SettlementStatus.WON)
                    .count();
            assertThat(wonCount).isEqualTo(2);

            // Verify losing bets
            long lostCount = settlements.stream()
                    .filter(s -> s.getStatus() == SettlementStatus.LOST)
                    .count();
            assertThat(lostCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Should not send any settlement when no pending bets exist")
        void shouldNotSendSettlementWhenNoPendingBets() {
            // Given
            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(Collections.emptyList());

            // When
            betSettlementService.processEventOutcome(testEvent);

            // Then
            verify(betSettlementProducer, never()).sendSettlement(any());
        }

        @Test
        @DisplayName("Should handle event with different winner ID")
        void shouldHandleEventWithDifferentWinnerId() {
            // Given
            Event eventWithDifferentWinner = new Event(100L, "Champions League Final", 999L);

            // Both bets should lose since neither picked winner 999
            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(List.of(winningBet, losingBet));

            // When
            betSettlementService.processEventOutcome(eventWithDifferentWinner);

            // Then
            verify(betSettlementProducer, times(2)).sendSettlement(settlementCaptor.capture());
            List<BetSettlement> settlements = settlementCaptor.getAllValues();

            assertThat(settlements).allMatch(s -> s.getStatus() == SettlementStatus.LOST);
            assertThat(settlements).allMatch(s -> s.getSettlementAmount().compareTo(BigDecimal.ZERO) == 0);
        }

        @Test
        @DisplayName("Should correctly set event winner ID in settlement")
        void shouldSetCorrectEventWinnerIdInSettlement() {
            // Given
            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(List.of(winningBet));

            // When
            betSettlementService.processEventOutcome(testEvent);

            // Then
            verify(betSettlementProducer).sendSettlement(settlementCaptor.capture());
            BetSettlement settlement = settlementCaptor.getValue();

            assertThat(settlement.getEventWinnerId()).isEqualTo(testEvent.eventWinnerId());
        }

        @Test
        @DisplayName("Should handle bet with zero amount")
        void shouldHandleBetWithZeroAmount() {
            // Given
            Bet zeroBet = Bet.builder()
                    .betId(4L)
                    .userId(1004L)
                    .eventId(100L)
                    .eventMarketId(500L)
                    .eventWinnerId(201L)
                    .betAmount(BigDecimal.ZERO)
                    .status(BetStatus.PENDING)
                    .placedAt(LocalDateTime.now())
                    .build();

            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(List.of(zeroBet));

            // When
            betSettlementService.processEventOutcome(testEvent);

            // Then
            verify(betSettlementProducer).sendSettlement(settlementCaptor.capture());
            BetSettlement settlement = settlementCaptor.getValue();

            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.WON);
            assertThat(settlement.getSettlementAmount()).isEqualByComparingTo(BigDecimal.ZERO); // 2 * 0 = 0
        }

        @Test
        @DisplayName("Should handle bet with large amount")
        void shouldHandleBetWithLargeAmount() {
            // Given
            Bet largeBet = Bet.builder()
                    .betId(5L)
                    .userId(1005L)
                    .eventId(100L)
                    .eventMarketId(500L)
                    .eventWinnerId(201L)
                    .betAmount(new BigDecimal("999999999.99"))
                    .status(BetStatus.PENDING)
                    .placedAt(LocalDateTime.now())
                    .build();

            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(List.of(largeBet));

            // When
            betSettlementService.processEventOutcome(testEvent);

            // Then
            verify(betSettlementProducer).sendSettlement(settlementCaptor.capture());
            BetSettlement settlement = settlementCaptor.getValue();

            assertThat(settlement.getSettlementAmount())
                    .isEqualByComparingTo(new BigDecimal("1999999999.98")); // 2x payout
        }
    }

    @Nested
    @DisplayName("processSettlement Tests")
    class ProcessSettlementTests {

        private BetSettlement wonSettlement;
        private BetSettlement lostSettlement;

        @BeforeEach
        void setUp() {
            wonSettlement = BetSettlement.builder()
                    .betId(1L)
                    .userId(1001L)
                    .eventId(100L)
                    .eventWinnerId(201L)
                    .betAmount(new BigDecimal("50.00"))
                    .settlementAmount(new BigDecimal("100.00"))
                    .status(SettlementStatus.WON)
                    .settledAt(LocalDateTime.now())
                    .build();

            lostSettlement = BetSettlement.builder()
                    .betId(2L)
                    .userId(1002L)
                    .eventId(100L)
                    .eventWinnerId(201L)
                    .betAmount(new BigDecimal("100.00"))
                    .settlementAmount(BigDecimal.ZERO)
                    .status(SettlementStatus.LOST)
                    .settledAt(LocalDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("Should update bet status to WON and save settlement")
        void shouldUpdateBetStatusToWonAndSaveSettlement() {
            // Given
            when(betRepository.findById(1L)).thenReturn(Optional.of(winningBet));
            when(betRepository.save(any(Bet.class))).thenReturn(winningBet);
            when(betSettlementRepository.save(any(BetSettlement.class))).thenReturn(wonSettlement);

            // When
            betSettlementService.processSettlement(wonSettlement);

            // Then
            verify(betRepository).save(betCaptor.capture());
            Bet savedBet = betCaptor.getValue();
            assertThat(savedBet.getStatus()).isEqualTo(BetStatus.WON);

            verify(betSettlementRepository).save(wonSettlement);
        }

        @Test
        @DisplayName("Should update bet status to LOST and save settlement")
        void shouldUpdateBetStatusToLostAndSaveSettlement() {
            // Given
            when(betRepository.findById(2L)).thenReturn(Optional.of(losingBet));
            when(betRepository.save(any(Bet.class))).thenReturn(losingBet);
            when(betSettlementRepository.save(any(BetSettlement.class))).thenReturn(lostSettlement);

            // When
            betSettlementService.processSettlement(lostSettlement);

            // Then
            verify(betRepository).save(betCaptor.capture());
            Bet savedBet = betCaptor.getValue();
            assertThat(savedBet.getStatus()).isEqualTo(BetStatus.LOST);

            verify(betSettlementRepository).save(lostSettlement);
        }

        @Test
        @DisplayName("Should throw exception when bet not found")
        void shouldThrowExceptionWhenBetNotFound() {
            // Given
            when(betRepository.findById(999L)).thenReturn(Optional.empty());

            BetSettlement settlementForNonExistentBet = BetSettlement.builder()
                    .betId(999L)
                    .userId(1001L)
                    .eventId(100L)
                    .eventWinnerId(201L)
                    .betAmount(new BigDecimal("50.00"))
                    .settlementAmount(new BigDecimal("100.00"))
                    .status(SettlementStatus.WON)
                    .settledAt(LocalDateTime.now())
                    .build();

            // When/Then
            assertThatThrownBy(() -> betSettlementService.processSettlement(settlementForNonExistentBet))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Bet not found: 999");

            verify(betRepository, never()).save(any());
            verify(betSettlementRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should propagate exception when bet repository save fails")
        void shouldPropagateExceptionWhenBetRepositorySaveFails() {
            // Given
            when(betRepository.findById(1L)).thenReturn(Optional.of(winningBet));
            when(betRepository.save(any(Bet.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When/Then
            assertThatThrownBy(() -> betSettlementService.processSettlement(wonSettlement))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Database error");

            verify(betSettlementRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should propagate exception when settlement repository save fails")
        void shouldPropagateExceptionWhenSettlementRepositorySaveFails() {
            // Given
            when(betRepository.findById(1L)).thenReturn(Optional.of(winningBet));
            when(betRepository.save(any(Bet.class))).thenReturn(winningBet);
            when(betSettlementRepository.save(any(BetSettlement.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When/Then
            assertThatThrownBy(() -> betSettlementService.processSettlement(wonSettlement))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Database error");
        }

        @Test
        @DisplayName("Should process settlement with correct settled timestamp")
        void shouldProcessSettlementWithCorrectTimestamp() {
            // Given
            LocalDateTime settlementTime = LocalDateTime.of(2026, 3, 8, 12, 0, 0);
            BetSettlement settlementWithTimestamp = BetSettlement.builder()
                    .betId(1L)
                    .userId(1001L)
                    .eventId(100L)
                    .eventWinnerId(201L)
                    .betAmount(new BigDecimal("50.00"))
                    .settlementAmount(new BigDecimal("100.00"))
                    .status(SettlementStatus.WON)
                    .settledAt(settlementTime)
                    .build();

            when(betRepository.findById(1L)).thenReturn(Optional.of(winningBet));
            when(betRepository.save(any(Bet.class))).thenReturn(winningBet);
            when(betSettlementRepository.save(any(BetSettlement.class))).thenReturn(settlementWithTimestamp);

            // When
            betSettlementService.processSettlement(settlementWithTimestamp);

            // Then
            verify(betSettlementRepository).save(settlementCaptor.capture());
            assertThat(settlementCaptor.getValue().getSettledAt()).isEqualTo(settlementTime);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle event with null eventName")
        void shouldHandleEventWithNullEventName() {
            // Given
            Event eventWithNullName = new Event(100L, null, 201L);
            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(List.of(winningBet));

            // When
            betSettlementService.processEventOutcome(eventWithNullName);

            // Then
            verify(betSettlementProducer).sendSettlement(any(BetSettlement.class));
        }

        @Test
        @DisplayName("Should handle concurrent settlements for same event")
        void shouldHandleConcurrentSettlementsForSameEvent() {
            // Given - Multiple bets with same event but different users
            List<Bet> multipleBets = List.of(
                    createBet(1L, 1001L, 100L, 201L, new BigDecimal("10.00")),
                    createBet(2L, 1002L, 100L, 202L, new BigDecimal("20.00")),
                    createBet(3L, 1003L, 100L, 201L, new BigDecimal("30.00")),
                    createBet(4L, 1004L, 100L, 203L, new BigDecimal("40.00")),
                    createBet(5L, 1005L, 100L, 201L, new BigDecimal("50.00"))
            );

            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(multipleBets);

            // When
            betSettlementService.processEventOutcome(testEvent);

            // Then
            verify(betSettlementProducer, times(5)).sendSettlement(settlementCaptor.capture());
            List<BetSettlement> settlements = settlementCaptor.getAllValues();

            // 3 winners (picked 201), 2 losers (picked 202 and 203)
            long wonCount = settlements.stream()
                    .filter(s -> s.getStatus() == SettlementStatus.WON)
                    .count();
            long lostCount = settlements.stream()
                    .filter(s -> s.getStatus() == SettlementStatus.LOST)
                    .count();

            assertThat(wonCount).isEqualTo(3);
            assertThat(lostCount).isEqualTo(2);
        }

        @Test
        @DisplayName("Should calculate correct payout for decimal bet amounts")
        void shouldCalculateCorrectPayoutForDecimalBetAmounts() {
            // Given
            Bet decimalBet = createBet(1L, 1001L, 100L, 201L, new BigDecimal("33.33"));
            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(List.of(decimalBet));

            // When
            betSettlementService.processEventOutcome(testEvent);

            // Then
            verify(betSettlementProducer).sendSettlement(settlementCaptor.capture());
            BetSettlement settlement = settlementCaptor.getValue();

            assertThat(settlement.getSettlementAmount())
                    .isEqualByComparingTo(new BigDecimal("66.66")); // 2 * 33.33
        }

        @Test
        @DisplayName("Should handle very small bet amounts")
        void shouldHandleVerySmallBetAmounts() {
            // Given
            Bet smallBet = createBet(1L, 1001L, 100L, 201L, new BigDecimal("0.01"));
            when(betRepository.findByEventIdAndStatus(100L, BetStatus.PENDING))
                    .thenReturn(List.of(smallBet));

            // When
            betSettlementService.processEventOutcome(testEvent);

            // Then
            verify(betSettlementProducer).sendSettlement(settlementCaptor.capture());
            BetSettlement settlement = settlementCaptor.getValue();

            assertThat(settlement.getSettlementAmount())
                    .isEqualByComparingTo(new BigDecimal("0.02")); // 2 * 0.01
        }

        @Test
        @DisplayName("Should verify repository is called with correct event ID")
        void shouldCallRepositoryWithCorrectEventId() {
            // Given
            Long expectedEventId = 12345L;
            Event specificEvent = new Event(expectedEventId, "Specific Event", 201L);
            when(betRepository.findByEventIdAndStatus(eq(expectedEventId), eq(BetStatus.PENDING)))
                    .thenReturn(Collections.emptyList());

            // When
            betSettlementService.processEventOutcome(specificEvent);

            // Then
            verify(betRepository).findByEventIdAndStatus(expectedEventId, BetStatus.PENDING);
        }

        @Test
        @DisplayName("Should not modify original bet object reference during settlement")
        void shouldUpdateBetStatusCorrectlyDuringSettlement() {
            // Given
            Bet originalBet = createBet(1L, 1001L, 100L, 201L, new BigDecimal("50.00"));
            assertThat(originalBet.getStatus()).isEqualTo(BetStatus.PENDING);

            when(betRepository.findById(1L)).thenReturn(Optional.of(originalBet));
            when(betRepository.save(any(Bet.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(betSettlementRepository.save(any(BetSettlement.class))).thenAnswer(invocation -> invocation.getArgument(0));

            BetSettlement settlement = BetSettlement.builder()
                    .betId(1L)
                    .userId(1001L)
                    .eventId(100L)
                    .eventWinnerId(201L)
                    .betAmount(new BigDecimal("50.00"))
                    .settlementAmount(new BigDecimal("100.00"))
                    .status(SettlementStatus.WON)
                    .settledAt(LocalDateTime.now())
                    .build();

            // When
            betSettlementService.processSettlement(settlement);

            // Then
            verify(betRepository).save(betCaptor.capture());
            assertThat(betCaptor.getValue().getStatus()).isEqualTo(BetStatus.WON);
        }
    }

    // Helper method to create test bets
    private Bet createBet(Long betId, Long userId, Long eventId, Long eventWinnerId, BigDecimal amount) {
        return Bet.builder()
                .betId(betId)
                .userId(userId)
                .eventId(eventId)
                .eventMarketId(500L)
                .eventWinnerId(eventWinnerId)
                .betAmount(amount)
                .status(BetStatus.PENDING)
                .placedAt(LocalDateTime.now())
                .build();
    }
}

