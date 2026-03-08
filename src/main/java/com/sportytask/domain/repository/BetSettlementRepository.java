package com.sportytask.domain.repository;

import com.sportytask.domain.entities.BetSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BetSettlementRepository extends JpaRepository<BetSettlement, Long> {

    /**
     * Find all settlements for a specific event
     */
    List<BetSettlement> findByEventId(Long eventId);

    /**
     * Find all settlements for a specific user
     */
    List<BetSettlement> findByUserId(Long userId);

    /**
     * Find settlement by bet ID
     */
    Optional<BetSettlement> findByBetId(Long betId);
}

