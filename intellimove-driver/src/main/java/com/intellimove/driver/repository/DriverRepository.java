package com.intellimove.driver.repository;

import com.intellimove.common.enums.DriverStatus;
import com.intellimove.driver.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    Optional<Driver> findByUserId(UUID userId);

    boolean existsByLicenseNumber(String licenseNumber);

    List<Driver> findByStatus(DriverStatus status);

    @Query("SELECT d FROM Driver d WHERE d.status IN ('ONLINE', 'AVAILABLE') AND d.verified = true")
    List<Driver> findAvailableDrivers();

    @Query("SELECT d FROM Driver d WHERE d.userId = :userId AND d.status = :status")
    Optional<Driver> findByUserIdAndStatus(@Param("userId") UUID userId,
                                           @Param("status") DriverStatus status);
}
