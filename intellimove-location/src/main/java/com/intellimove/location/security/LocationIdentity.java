package com.intellimove.location.security;

import com.intellimove.common.security.SecurityUtils;

/**
 * Canonical identity for Redis GEO, matching, and ride assignment.
 *
 * Ride.driverId, JWT principal, and location GEO members are the driver's
 * <strong>user ID</strong> (auth user), not {@code Driver.id} (driver profile).
 * Matching assigns the GEO member onto the ride; accept/start/complete then
 * compare that value to the JWT user ID. Using a profile ID as a location key
 * would make matching assign an ID the driver cannot act on.
 */
public final class LocationIdentity {

    public static final String MISMATCH_MESSAGE =
            "Cannot update another driver's location. "
                    + "The path driverId must be the authenticated driver user ID (JWT), "
                    + "not the driver profile ID.";

    private LocationIdentity() {}

    /**
     * @return authenticated driver user ID, or {@code null} if unauthenticated
     */
    public static String currentDriverUserId() {
        return SecurityUtils.getCurrentUserIdString();
    }

    /**
     * @return true when {@code pathDriverId} is the authenticated user ID
     */
    public static boolean matchesAuthenticatedDriver(String pathDriverId) {
        String userId = currentDriverUserId();
        return userId != null && userId.equals(pathDriverId);
    }

    public static boolean isAdmin() {
        return SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN");
    }
}
