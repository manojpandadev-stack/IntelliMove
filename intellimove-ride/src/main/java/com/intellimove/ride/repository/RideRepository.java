package com.intellimove.ride.repository;

import com.intellimove.common.enums.RideStatus;
import com.intellimove.ride.entity.Ride;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RideRepository extends JpaRepository<Ride, UUID> {

    Page<Ride> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<Ride> findByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable pageable);

    Page<Ride> findByStatusOrderByCreatedAtDesc(RideStatus status, Pageable pageable);

    @Query("SELECT r FROM Ride r WHERE r.status = :status AND r.driverId IS NULL")
    List<Ride> findUnassignedRidesByStatus(@Param("status") RideStatus status);

    Optional<Ride> findByCustomerIdAndStatusIn(UUID customerId, List<RideStatus> activeStatuses);

    Optional<Ride> findByDriverIdAndStatusIn(UUID driverId, List<RideStatus> activeStatuses);

    @Query("SELECT r FROM Ride r WHERE r.createdAt BETWEEN :from AND :to")
    Page<Ride> findByDateRange(@Param("from") java.time.Instant from,
                               @Param("to") java.time.Instant to,
                               Pageable pageable);

    @Query("SELECT COUNT(r) FROM Ride r WHERE r.status = :status")
    long countByStatus(@Param("status") RideStatus status);

    @Query("SELECT COUNT(r) FROM Ride r WHERE r.createdAt BETWEEN :from AND :to")
    long countByDateRange(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("SELECT COALESCE(SUM(r.finalFare), 0) FROM Ride r WHERE r.status = 'TRIP_COMPLETED' AND r.tripCompletedAt BETWEEN :from AND :to")
    java.math.BigDecimal sumRevenueByDateRange(@Param("from") java.time.Instant from,
                                                @Param("to") java.time.Instant to);
}
