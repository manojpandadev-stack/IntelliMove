package com.intellimove.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = :status, e.processedAt = :processedAt WHERE e.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") OutboxStatus status,
                     @Param("processedAt") Instant processedAt);

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = :status, e.retryCount = e.retryCount + 1 WHERE e.id = :id")
    int incrementRetryAndMarkFailed(@Param("id") UUID id, @Param("status") OutboxStatus status);

    List<OutboxEvent> findByStatusAndRetryCountLessThan(OutboxStatus status, int maxRetries);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PROCESSED' AND e.processedAt < :before")
    int deleteOldProcessedEvents(@Param("before") Instant before);
}
