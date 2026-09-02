package com.intellimove.ride.service;

import com.intellimove.common.dto.PagedResponse;
import com.intellimove.common.enums.DomainEventType;
import com.intellimove.common.enums.RideStatus;
import com.intellimove.common.enums.RideType;
import com.intellimove.common.event.*;
import com.intellimove.common.exception.InvalidStateTransitionException;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.common.outbox.OutboxService;
import com.intellimove.ride.dto.CancelRideRequest;
import com.intellimove.ride.dto.CreateRideRequest;
import com.intellimove.ride.dto.FareEstimateResponse;
import com.intellimove.ride.dto.RideEtaContext;
import com.intellimove.ride.dto.RideResponse;
import com.intellimove.ride.entity.Ride;
import com.intellimove.ride.mapper.RideMapper;
import com.intellimove.ride.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Ride lifecycle management with enforced state machine.
 *
 * Valid transitions:
 *   REQUESTED       -> MATCHING, DRIVER_ASSIGNED, CANCELLED
 *   MATCHING        -> DRIVER_ASSIGNED, CANCELLED
 *   DRIVER_ASSIGNED -> DRIVER_ACCEPTED, REQUESTED (driver rejected), CANCELLED
 *   DRIVER_ACCEPTED -> DRIVER_ARRIVING, CANCELLED
 *   DRIVER_ARRIVING -> TRIP_STARTED
 *   TRIP_STARTED    -> TRIP_COMPLETED
 *   TRIP_COMPLETED  -> (terminal)
 *   CANCELLED       -> (terminal)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RideService {

    private final RideRepository rideRepository;
    private final RideMapper rideMapper;
    private final PricingService pricingService;
    private final OutboxService outboxService;

    private static final Map<RideStatus, Set<RideStatus>> VALID_TRANSITIONS = Map.of(
            RideStatus.REQUESTED, Set.of(RideStatus.MATCHING, RideStatus.DRIVER_ASSIGNED, RideStatus.CANCELLED),
            RideStatus.MATCHING, Set.of(RideStatus.DRIVER_ASSIGNED, RideStatus.CANCELLED),
            RideStatus.DRIVER_ASSIGNED, Set.of(RideStatus.DRIVER_ACCEPTED, RideStatus.REQUESTED, RideStatus.CANCELLED),
            RideStatus.DRIVER_ACCEPTED, Set.of(RideStatus.DRIVER_ARRIVING, RideStatus.TRIP_STARTED, RideStatus.CANCELLED),
            RideStatus.DRIVER_ARRIVING, Set.of(RideStatus.TRIP_STARTED),
            RideStatus.TRIP_STARTED, Set.of(RideStatus.TRIP_COMPLETED),
            RideStatus.TRIP_COMPLETED, Set.of(),
            RideStatus.CANCELLED, Set.of()
    );

    /**
     * Fare preview for the booking UI. Uses the exact same pricing engine as
     * ride creation so the displayed estimate matches the stored estimatedFare.
     */
    @Transactional(readOnly = true)
    public FareEstimateResponse estimateFare(double pickupLat, double pickupLng,
                                             double dropoffLat, double dropoffLng) {
        double distanceKm = pricingService.haversineDistance(pickupLat, pickupLng, dropoffLat, dropoffLng);
        long minutes = (long) (distanceKm / 0.5); // same avg-speed assumption as PricingService
        List<FareEstimateResponse.RideOptionEstimate> options = new ArrayList<>();
        for (RideType type : List.of(RideType.ECONOMY, RideType.COMFORT, RideType.PREMIUM, RideType.XL)) {
            options.add(FareEstimateResponse.RideOptionEstimate.builder()
                    .rideType(type)
                    .estimatedFare(pricingService.calculateEstimate(pickupLat, pickupLng, dropoffLat, dropoffLng, type))
                    .etaMinutes(minutes)
                    .capacity(type == RideType.XL ? 6 : 4)
                    .description(describeRideType(type))
                    .surgeMultiplier(pricingService.getDemandMultiplier())
                    .build());
        }
        return FareEstimateResponse.builder()
                .distanceKm(Math.round(distanceKm * 10.0) / 10.0)
                .estimatedMinutes(minutes)
                .currency("USD")
                .options(options)
                .build();
    }

    /**
     * Deterministic per-category description shown in the booking UI.
     * Mirrors the wording already used by the rider dashboard so the API and
     * the frontend stay consistent. Purely presentational metadata.
     */
    private static String describeRideType(RideType rideType) {
        return switch (rideType) {
            case ECONOMY -> "Affordable everyday rides";
            case COMFORT -> "Newer cars, extra legroom";
            case PREMIUM -> "Top-rated drivers, luxury cars";
            case XL -> "Room for larger groups";
            case DELIVERY -> "Package and food deliveries";
        };
    }

    @Transactional
    public RideResponse requestRide(UUID customerId, CreateRideRequest request) {
        // Check for active ride
        List<RideStatus> activeStatuses = List.of(
                RideStatus.REQUESTED, RideStatus.MATCHING, RideStatus.DRIVER_ASSIGNED,
                RideStatus.DRIVER_ACCEPTED, RideStatus.DRIVER_ARRIVING, RideStatus.TRIP_STARTED);
        Optional<Ride> activeRide = rideRepository.findByCustomerIdAndStatusIn(customerId, activeStatuses);
        if (activeRide.isPresent()) {
            throw new com.intellimove.common.exception.BusinessException(
                    "ACTIVE_RIDE_EXISTS", "You already have an active ride");
        }

        BigDecimal estimatedFare = pricingService.calculateEstimate(
                request.getPickupLatitude(), request.getPickupLongitude(),
                request.getDropoffLatitude(), request.getDropoffLongitude(),
                request.getRideType());

        Ride ride = Ride.builder()
                .customerId(customerId)
                .status(RideStatus.REQUESTED)
                .rideType(request.getRideType())
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .pickupAddress(request.getPickupAddress())
                .dropoffLatitude(request.getDropoffLatitude())
                .dropoffLongitude(request.getDropoffLongitude())
                .dropoffAddress(request.getDropoffAddress())
                .estimatedFare(estimatedFare)
                .currency("USD")
                .build();

        ride = rideRepository.save(ride);

        outboxService.saveEvent(
                RideRequestedEvent.builder()
                        .eventType(DomainEventType.RIDE_REQUESTED.name())
                        .rideId(ride.getId().toString())
                        .customerId(customerId.toString())
                        .pickupLatitude(request.getPickupLatitude())
                        .pickupLongitude(request.getPickupLongitude())
                        .dropoffLatitude(request.getDropoffLatitude())
                        .dropoffLongitude(request.getDropoffLongitude())
                        .rideType(request.getRideType().name())
                        .pickupAddress(request.getPickupAddress())
                        .dropoffAddress(request.getDropoffAddress())
                        .correlationId(ride.getId().toString())
                        .build(),
                "Ride", ride.getId().toString(),
                "ride-events", ride.getId().toString());

        log.info("Ride requested: id={}, customerId={}", ride.getId(), customerId);
        return rideMapper.toResponse(ride);
    }

    @Transactional
    public RideResponse cancelRide(UUID rideId, UUID userId, CancelRideRequest request) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

        if (!ride.getCustomerId().equals(userId) && (ride.getDriverId() == null || !ride.getDriverId().equals(userId))) {
            throw new com.intellimove.common.exception.UnauthorizedException(
                    "User not authorized to cancel this ride");
        }

        validateTransition(ride.getStatus(), RideStatus.CANCELLED);

        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(Instant.now());
        ride.setCancellationReason(request.getReason());
        ride.setCancellationNote(request.getNote());
        ride.setCancelledBy(userId.equals(ride.getCustomerId()) ? "CUSTOMER" : "DRIVER");
        ride = rideRepository.save(ride);

        outboxService.saveEvent(
                RideCancelledEvent.builder()
                        .eventType(DomainEventType.RIDE_CANCELLED.name())
                        .rideId(rideId.toString())
                        .driverId(ride.getDriverId() != null ? ride.getDriverId().toString() : null)
                        .customerId(ride.getCustomerId().toString())
                        .cancellationReason(request.getReason().name())
                        .cancelledBy(ride.getCancelledBy())
                        .correlationId(rideId.toString())
                        .build(),
                "Ride", rideId.toString(),
                "ride-events", rideId.toString());

        log.info("Ride cancelled: id={}, reason={}", rideId, request.getReason());
        return rideMapper.toResponse(ride);
    }

    @Transactional
    public RideResponse assignDriver(UUID rideId, UUID driverId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

        validateTransition(ride.getStatus(), RideStatus.DRIVER_ASSIGNED);

        ride.setDriverId(driverId);
        ride.setStatus(RideStatus.DRIVER_ASSIGNED);
        ride.setDriverAssignedAt(Instant.now());
        ride = rideRepository.save(ride);

        outboxService.saveEvent(
                DriverAssignedEvent.builder()
                        .eventType(DomainEventType.DRIVER_ASSIGNED.name())
                        .rideId(rideId.toString())
                        .driverId(driverId.toString())
                        .customerId(ride.getCustomerId().toString())
                        .correlationId(rideId.toString())
                        .build(),
                "Ride", rideId.toString(),
                "ride-events", rideId.toString());

        log.info("Driver assigned to ride: rideId={}, driverId={}", rideId, driverId);
        return rideMapper.toResponse(ride);
    }

    @Transactional
    public RideResponse driverAccept(UUID rideId, UUID driverId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

        if (ride.getDriverId() == null || !ride.getDriverId().equals(driverId)) {
            throw new com.intellimove.common.exception.BusinessException(
                    "NOT_ASSIGNED", "Driver is not assigned to this ride");
        }

        validateTransition(ride.getStatus(), RideStatus.DRIVER_ACCEPTED);
        ride.setStatus(RideStatus.DRIVER_ACCEPTED);
        ride.setDriverAcceptedAt(Instant.now());
        ride = rideRepository.save(ride);

        outboxService.saveEvent(
                RideCancelledEvent.builder()
                        .eventType(DomainEventType.DRIVER_ACCEPTED.name())
                        .rideId(rideId.toString())
                        .driverId(driverId.toString())
                        .customerId(ride.getCustomerId().toString())
                        .correlationId(rideId.toString())
                        .build(),
                "Ride", rideId.toString(),
                "ride-events", rideId.toString());

        log.info("Driver accepted ride: rideId={}, driverId={}", rideId, driverId);
        return rideMapper.toResponse(ride);
    }

    /**
     * Assigned driver rejects an incoming ride request. The ride is NOT
     * cancelled: it returns to REQUESTED with the assignment cleared so the
     * matching system can select another eligible driver (Uber-style
     * reassignment). Only the currently assigned driver may reject.
     */
    @Transactional
    public RideResponse driverReject(UUID rideId, UUID driverId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

        if (ride.getDriverId() == null || !ride.getDriverId().equals(driverId)) {
            throw new com.intellimove.common.exception.BusinessException(
                    "NOT_ASSIGNED", "Driver is not assigned to this ride");
        }

        validateTransition(ride.getStatus(), RideStatus.REQUESTED);

        ride.setStatus(RideStatus.REQUESTED);
        ride.setDriverId(null);
        ride.setDriverAssignedAt(null);
        ride = rideRepository.save(ride);

        outboxService.saveEvent(
                DriverRejectedEvent.builder()
                        .eventType(DomainEventType.DRIVER_REJECTED.name())
                        .rideId(rideId.toString())
                        .driverId(driverId.toString())
                        .customerId(ride.getCustomerId().toString())
                        .pickupLatitude(ride.getPickupLatitude())
                        .pickupLongitude(ride.getPickupLongitude())
                        .rideType(ride.getRideType() != null ? ride.getRideType().name() : null)
                        .correlationId(rideId.toString())
                        .build(),
                "Ride", rideId.toString(),
                "ride-events", rideId.toString());

        log.info("Driver rejected ride request: rideId={}, driverId={} — ride returned to REQUESTED for reassignment",
                rideId, driverId);
        return rideMapper.toResponse(ride);
    }

    @Transactional
    public RideResponse startTrip(UUID rideId, UUID driverId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

        if (ride.getDriverId() == null || !ride.getDriverId().equals(driverId)) {
            throw new com.intellimove.common.exception.BusinessException(
                    "NOT_ASSIGNED", "Driver is not assigned to this ride");
        }

        RideStatus nextStatus = ride.getStatus() == RideStatus.DRIVER_ACCEPTED
                ? RideStatus.TRIP_STARTED : RideStatus.TRIP_STARTED;
        validateTransition(ride.getStatus(), RideStatus.TRIP_STARTED);

        ride.setStatus(RideStatus.TRIP_STARTED);
        ride.setTripStartedAt(Instant.now());
        ride = rideRepository.save(ride);

        outboxService.saveEvent(
                RideRequestedEvent.builder()
                        .eventType(DomainEventType.RIDE_STARTED.name())
                        .rideId(rideId.toString())
                        .driverId(driverId.toString())
                        .customerId(ride.getCustomerId().toString())
                        .correlationId(rideId.toString())
                        .build(),
                "Ride", rideId.toString(),
                "ride-events", rideId.toString());

        log.info("Trip started: rideId={}", rideId);
        return rideMapper.toResponse(ride);
    }

    @Transactional
    public RideResponse completeTrip(UUID rideId, UUID driverId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));

        if (ride.getDriverId() == null || !ride.getDriverId().equals(driverId)) {
            throw new com.intellimove.common.exception.BusinessException(
                    "NOT_ASSIGNED", "Driver is not assigned to this ride");
        }

        validateTransition(ride.getStatus(), RideStatus.TRIP_COMPLETED);

        BigDecimal finalFare = pricingService.calculateFinalFare(
                ride.getPickupLatitude(), ride.getPickupLongitude(),
                ride.getDropoffLatitude(), ride.getDropoffLongitude(),
                ride.getRideType(),
                ride.getTripStartedAt() != null
                        ? java.time.Duration.between(ride.getTripStartedAt(), Instant.now()).toMinutes() : 0);

        long duration = ride.getTripStartedAt() != null
                ? java.time.Duration.between(ride.getTripStartedAt(), Instant.now()).toMinutes() : 0;

        ride.setStatus(RideStatus.TRIP_COMPLETED);
        ride.setTripCompletedAt(Instant.now());
        ride.setFinalFare(finalFare);
        ride.setDurationMinutes(duration);
        ride = rideRepository.save(ride);

        outboxService.saveEvent(
                RideCompletedEvent.builder()
                        .eventType(DomainEventType.RIDE_COMPLETED.name())
                        .rideId(rideId.toString())
                        .driverId(driverId.toString())
                        .customerId(ride.getCustomerId().toString())
                        .fareAmount(finalFare)
                        .currency("USD")
                        .distanceKm(ride.getDistanceKm())
                        .durationMinutes(duration)
                        .correlationId(rideId.toString())
                        .build(),
                "Ride", rideId.toString(),
                "ride-events", rideId.toString());

        log.info("Trip completed: rideId={}, fare={}", rideId, finalFare);
        return rideMapper.toResponse(ride);
    }

    @Transactional(readOnly = true)
    public RideResponse getRide(UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));
        return rideMapper.toResponse(ride);
    }

    /**
     * Minimal, read-only ride context for the Location Service's live pickup
     * ETA feature. Throws {@link ResourceNotFoundException} when the ride does
     * not exist (mapped to HTTP 404 by the controller).
     */
    @Transactional(readOnly = true)
    public RideEtaContext getEtaContext(UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride", "id", rideId));
        return new RideEtaContext(ride.getDriverId(), ride.getPickupLatitude(),
                ride.getPickupLongitude(), ride.getStatus());
    }

    @Transactional(readOnly = true)
    public PagedResponse<RideResponse> getCustomerRides(UUID customerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Ride> rides = rideRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
        return toPagedResponse(rides);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RideResponse> getDriverRides(UUID driverId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Ride> rides = rideRepository.findByDriverIdOrderByCreatedAtDesc(driverId, pageable);
        return toPagedResponse(rides);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RideResponse> getAllRides(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Ride> rides = rideRepository.findAll(pageable);
        return toPagedResponse(rides);
    }

    private void validateTransition(RideStatus current, RideStatus target) {
        Set<RideStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidStateTransitionException("Ride", current.name(), target.name());
        }
    }

    /**
     * Check if the given user is the customer or assigned driver for this ride.
     * Used for IDOR protection and WebSocket authorization.
     */
    @Transactional(readOnly = true)
    public boolean isUserAuthorizedForRide(UUID rideId, UUID userId) {
        if (rideId == null || userId == null) return false;
        return rideRepository.findById(rideId)
                .map(ride -> userId.equals(ride.getCustomerId())
                        || userId.equals(ride.getDriverId()))
                .orElse(false);
    }

    /**
     * True when the ride can still accept a driver assignment through its
     * state machine (REQUESTED or MATCHING). Used by the Location Service's
     * matching consumer to skip stale RIDE_REQUESTED events instantly.
     */
    @Transactional(readOnly = true)
    public boolean isAssignable(UUID rideId) {
        if (rideId == null) return false;
        return rideRepository.findById(rideId)
                .map(ride -> VALID_TRANSITIONS.getOrDefault(ride.getStatus(), Set.of())
                        .contains(RideStatus.DRIVER_ASSIGNED))
                .orElse(false);
    }

    private PagedResponse<RideResponse> toPagedResponse(Page<Ride> page) {
        return PagedResponse.<RideResponse>builder()
                .content(page.getContent().stream().map(rideMapper::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
