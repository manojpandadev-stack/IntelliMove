package com.intellimove.driver.controller;

import com.intellimove.common.dto.ApiResponse;
import com.intellimove.common.enums.DriverStatus;
import com.intellimove.common.security.SecurityUtils;
import com.intellimove.driver.dto.DriverResponse;
import com.intellimove.driver.dto.RegisterDriverRequest;
import com.intellimove.driver.dto.UpdateDriverStatusRequest;
import com.intellimove.driver.dto.UpdateLocationRequest;
import com.intellimove.driver.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DriverResponse>> registerDriver(
            @Valid @RequestBody RegisterDriverRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        DriverResponse driver = driverService.registerDriver(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Driver registered", driver));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DriverResponse>> getDriver(@PathVariable UUID id) {
        DriverResponse driver = driverService.getDriverById(id);
        return ResponseEntity.ok(ApiResponse.success(driver));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<DriverResponse>> getDriverByUserId(@PathVariable UUID userId) {
        DriverResponse driver = driverService.getDriverByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(driver));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDriverStatusRequest request) {
        DriverResponse driver = driverService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Status updated", driver));
    }

    @PostMapping("/{id}/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateLocation(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationRequest request) {
        DriverResponse driver = driverService.updateLocation(id, request.getLatitude(), request.getLongitude());
        return ResponseEntity.ok(ApiResponse.success(driver));
    }

    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'DRIVER')")
    public ResponseEntity<ApiResponse<List<DriverResponse>>> getAvailableDrivers() {
        List<DriverResponse> drivers = driverService.getAvailableDrivers();
        return ResponseEntity.ok(ApiResponse.success(drivers));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<DriverResponse>>> getDriversByStatus(
            @PathVariable DriverStatus status) {
        List<DriverResponse> drivers = driverService.getDriversByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(drivers));
    }

    @PatchMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DriverResponse>> verifyDriver(@PathVariable UUID id) {
        DriverResponse driver = driverService.verifyDriver(id);
        return ResponseEntity.ok(ApiResponse.success("Driver verified", driver));
    }

    @PostMapping("/{id}/rating")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<DriverResponse>> updateRating(
            @PathVariable UUID id,
            @RequestParam double rating) {
        DriverResponse driver = driverService.updateRating(id, rating);
        return ResponseEntity.ok(ApiResponse.success("Rating updated", driver));
    }
}
