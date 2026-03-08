package com.sportytask.domain.repository;

import com.sportytask.domain.entities.Bet;
import com.sportytask.domain.entities.BetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BetRepository extends JpaRepository<Bet, Long> {

    /**
     * Find all bets for a specific event with a given status
     */
    List<Bet> findByEventIdAndStatus(Long eventId, BetStatus status);

    /**
     * Find all bets for multiple events with a given status
     */
    @Query("SELECT b FROM Bet b WHERE b.eventId IN :eventIds AND b.status = :status")
    List<Bet> findByEventIdsAndStatus(@Param("eventIds") List<Long> eventIds,
                                       @Param("status") BetStatus status);

    /**
     * Count pending bets for an event
     */
    long countByEventIdAndStatus(Long eventId, BetStatus status);
}
