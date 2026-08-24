package com.intellimove.payment.repository;

import com.intellimove.common.enums.PaymentStatus;
import com.intellimove.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByRideId(UUID rideId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Page<Payment> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
