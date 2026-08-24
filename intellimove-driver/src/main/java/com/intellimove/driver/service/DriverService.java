package com.intellimove.driver.service;

import com.intellimove.common.enums.DriverStatus;
import com.intellimove.common.event.DriverStatusChangedEvent;
import com.intellimove.common.enums.DomainEventType;
import com.intellimove.common.event.EventPublisher;
import com.intellimove.common.exception.BusinessException;
import com.intellimove.common.exception.InvalidStateTransitionException;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.driver.dto.DriverResponse;
import com.intellimove.driver.dto.RegisterDriverRequest;
import com.intellimove.driver.entity.Driver;
import com.intellimove.driver.mapper.DriverMapper;
import com.intellimove.driver.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Driver management service with enforced state machine transitions.
 *
 * Valid transitions:
 *   OFFLINE  -> ONLINE, SUSPENDED
 *   ONLINE   -> AVAILABLE, OFFLINE, SUSPENDED
 *   AVAILABLE -> OFFERED, OFFLINE, ONLINE, SUSPENDED
 *   OFFERED  -> ON_TRIP, AVAILABLE, ONLINE
 *   ON_TRIP  -> AVAILABLE, ONLINE
 *   SUSPENDED -> OFFLINE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriverService {

    private final DriverRepository driverRepository;
    private final DriverMapper driverMapper;
    private final EventPublisher eventPublisher;

    private static final Map<DriverStatus, Set<DriverStatus>> VALID_TRANSITIONS = Map.of(
            DriverStatus.OFFLINE, Set.of(DriverStatus.ONLINE, DriverStatus.SUSPENDED),
            DriverStatus.ONLINE, Set.of(DriverStatus.AVAILABLE, DriverStatus.OFFLINE, DriverStatus.SUSPENDED),
            DriverStatus.AVAILABLE, Set.of(DriverStatus.OFFERED, DriverStatus.OFFLINE, DriverStatus.ONLINE, DriverStatus.SUSPENDED),
            DriverStatus.OFFERED, Set.of(DriverStatus.ON_TRIP, DriverStatus.AVAILABLE, DriverStatus.ONLINE),
            DriverStatus.ON_TRIP, Set.of(DriverStatus.AVAILABLE, DriverStatus.ONLINE),
            DriverStatus.SUSPENDED, Set.of(DriverStatus.OFFLINE)
    );

    @Transactional
    public DriverResponse registerDriver(UUID userId, RegisterDriverRequest request) {
        if (driverRepository.findByUserId(userId).isPresent()) {
            throw new BusinessException("ALREADY_REGISTERED", "User is already registered as a driver");
        }
        if (driverRepository.existsByLicenseNumber(request.getLicenseNumber())) {
            throw new BusinessException("LICENSE_EXISTS", "License number already registered");
        }

        Driver driver = Driver.builder()
                .userId(userId)
                .licenseNumber(request.getLicenseNumber())
                .vehicleMake(request.getVehicleMake())
                .vehicleModel(request.getVehicleModel())
                .vehicleYear(request.getVehicleYear())
                .vehicleColor(request.getVehicleColor())
                .licensePlate(request.getLicensePlate())
                .vehicleType(request.getVehicleType())
                .status(DriverStatus.OFFLINE)
                .verified(false)
                .available(false)
                .rating(5.0)
                .totalTrips(0)
                .build();

        driver = driverRepository.save(driver);
        log.info("Driver registered: userId={}, driverId={}", userId, driver.getId());
        return driverMapper.toResponse(driver);
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriverById(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", driverId));
        return driverMapper.toResponse(driver);
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriverByUserId(UUID userId) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "userId", userId));
        return driverMapper.toResponse(driver);
    }

    @Transactional
    public DriverResponse updateStatus(UUID driverId, DriverStatus newStatus) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", driverId));

        DriverStatus currentStatus = driver.getStatus();

        if (!canTransition(currentStatus, newStatus)) {
            throw new InvalidStateTransitionException("Driver", currentStatus.name(), newStatus.name());
        }

        driver.setStatus(newStatus);
        driver.setAvailable(newStatus == DriverStatus.AVAILABLE || newStatus == DriverStatus.ONLINE);
        driver = driverRepository.save(driver);

        eventPublisher.publish(DriverStatusChangedEvent.builder()
                .eventType(DomainEventType.DRIVER_STATUS_CHANGED.name())
                .driverId(driverId.toString())
                .previousStatus(currentStatus.name())
                .newStatus(newStatus.name())
                .build());

        log.info("Driver {} status: {} -> {}", driverId, currentStatus, newStatus);
        return driverMapper.toResponse(driver);
    }

    @Transactional
    public DriverResponse updateLocation(UUID driverId, double latitude, double longitude) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", driverId));
        driver.setLastLocationUpdateAt(Instant.now());
        driver = driverRepository.save(driver);
        return driverMapper.toResponse(driver);
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> getAvailableDrivers() {
        return driverRepository.findAvailableDrivers().stream()
                .map(driverMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> getDriversByStatus(DriverStatus status) {
        return driverRepository.findByStatus(status).stream()
                .map(driverMapper::toResponse)
                .toList();
    }

    @Transactional
    public DriverResponse verifyDriver(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", driverId));
        driver.setVerified(true);
        driver.setVerifiedAt(Instant.now());
        driver = driverRepository.save(driver);
        log.info("Driver verified: {}", driverId);
        return driverMapper.toResponse(driver);
    }

    @Transactional
    public DriverResponse updateRating(UUID driverId, double newRating) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", driverId));
        double totalScore = driver.getRating() * driver.getTotalRatings() + newRating;
        driver.setTotalRatings(driver.getTotalRatings() + 1);
        driver.setRating(totalScore / driver.getTotalRatings());
        driver = driverRepository.save(driver);
        return driverMapper.toResponse(driver);
    }

    private boolean canTransition(DriverStatus from, DriverStatus to) {
        Set<DriverStatus> allowed = VALID_TRANSITIONS.getOrDefault(from, Set.of());
        return allowed.contains(to);
    }
}
