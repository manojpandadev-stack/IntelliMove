package com.intellimove.location.service;

import com.intellimove.location.handler.LocationWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Manages real-time driver locations using Redis GEO commands.
 * High-frequency GPS updates go to Redis only, not PostgreSQL.
 *
 * NOTE: Uses StringRedisTemplate (StringRedisSerializer for both keys and values)
 * because Redis GEO members are always stored as plain strings. Using a
 * GenericJackson2JsonRedisSerializer-based template causes the member names
 * to fail JSON deserialization, resulting in zero results from radius queries.
 */
@Service
@Slf4j
public class DriverLocationService {

    private final StringRedisTemplate redisTemplate;
    private final LocationWebSocketHandler webSocketHandler;

    private static final String GEO_KEY = "driver:locations";
    private static final String DRIVER_HASH_KEY_PREFIX = "driver:details:";
    private static final String DRIVER_RIDE_KEY_PREFIX = "driver:current_ride:";
    private static final long LOCATION_TTL_MINUTES = 30;
    /**
     * A driver whose last GPS heartbeat is older than this is considered gone
     * (app closed, network lost) and must be excluded from matching. The
     * driver's details hash expires after LOCATION_TTL_MINUTES, but the shared
     * GEO sorted set has no per-member expiry, so stale members must be
     * filtered and evicted here.
     */
    private static final long HEARTBEAT_STALE_MS = 5 * 60 * 1000L;

    @Autowired
    public DriverLocationService(StringRedisTemplate redisTemplate,
                                 @Lazy LocationWebSocketHandler webSocketHandler) {
        this.redisTemplate = redisTemplate;
        this.webSocketHandler = webSocketHandler;
    }

    public void updateDriverLocation(String driverId, double latitude, double longitude,
                                     Map<String, String> metadata) {
        updateDriverLocation(driverId, latitude, longitude, metadata, null);
    }

    /**
     * Update driver location in Redis GEO and optionally associate with a ride.
     * The rideId is stored so that location broadcasts can be routed to the
     * correct ride subscribers via WebSocket.
     */
    public void updateDriverLocation(String driverId, double latitude, double longitude,
                                     Map<String, String> metadata, String rideId) {
        Point point = new Point(longitude, latitude);
        redisTemplate.opsForGeo().add(GEO_KEY, point, driverId);

        Map<String, String> details = new HashMap<>(metadata);
        details.put("latitude", String.valueOf(latitude));
        details.put("longitude", String.valueOf(longitude));
        details.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().putAll(DRIVER_HASH_KEY_PREFIX + driverId, details);

        if (rideId != null && !rideId.isBlank()) {
            redisTemplate.opsForValue().set(DRIVER_RIDE_KEY_PREFIX + driverId, rideId,
                    LOCATION_TTL_MINUTES, TimeUnit.MINUTES);
        }

        redisTemplate.expire(GEO_KEY, LOCATION_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.expire(DRIVER_HASH_KEY_PREFIX + driverId, LOCATION_TTL_MINUTES, TimeUnit.MINUTES);

        // Broadcast to WebSocket subscribers of this ride ? this is the missing caller
        // that previously caused "zero location messages received" by WebSocket clients.
        if (rideId != null && !rideId.isBlank() && webSocketHandler != null) {
            try {
                webSocketHandler.broadcastDriverLocation(rideId, driverId, latitude, longitude);
            } catch (Exception e) {
                log.warn("Failed to broadcast driver location via WebSocket: {}", e.getMessage());
            }
        }
    }

    public String getDriverRideId(String driverId) {
        return redisTemplate.opsForValue().get(DRIVER_RIDE_KEY_PREFIX + driverId);
    }

    /**
     * True when the driver is currently tied to an active ride (assignment,
     * accepted trip, etc.). Used by matching so busy drivers are never offered
     * to new ride requests.
     */
    public boolean isDriverBusy(String driverId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(DRIVER_RIDE_KEY_PREFIX + driverId));
    }

    /**
     * Marks the driver as committed to a ride for the purpose of matching.
     * The marker expires with the location TTL as a safety net; lifecycle
     * events (completion/cancellation) clear it explicitly.
     */
    public void associateDriverWithRide(String driverId, String rideId) {
        redisTemplate.opsForValue().set(DRIVER_RIDE_KEY_PREFIX + driverId, rideId,
                LOCATION_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Frees the driver for new matches after a ride completes or is cancelled.
     */
    public void clearDriverRide(String driverId) {
        redisTemplate.delete(DRIVER_RIDE_KEY_PREFIX + driverId);
    }

    public List<DriverLocation> findNearbyDrivers(double latitude, double longitude, double radiusKm) {
        Distance distance = new Distance(radiusKm, RedisGeoCommands.DistanceUnit.KILOMETERS);
        Circle circle = new Circle(new Point(longitude, latitude), distance);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                redisTemplate.opsForGeo().radius(GEO_KEY, circle,
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeCoordinates()
                                .includeDistance()
                                .sortAscending());

        List<DriverLocation> nearby = new ArrayList<>();
        if (results != null) {
            long now = System.currentTimeMillis();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
                String driverId = result.getContent().getName();
                Map<Object, Object> details = redisTemplate.opsForHash()
                        .entries(DRIVER_HASH_KEY_PREFIX + driverId);

                // Staleness guard: skip (and evict) drivers with no live heartbeat.
                // Their details hash has expired or their last GPS update is too old,
                // so they are no longer actually on the road.
                if (details.isEmpty()) {
                    redisTemplate.opsForGeo().remove(GEO_KEY, driverId);
                    log.info("Evicted stale driver {} from matching (no heartbeat data)", driverId);
                    continue;
                }
                String updatedAt = String.valueOf(details.get("updatedAt"));
                try {
                    long lastBeat = Long.parseLong(updatedAt);
                    if (now - lastBeat > HEARTBEAT_STALE_MS) {
                        redisTemplate.opsForGeo().remove(GEO_KEY, driverId);
                        log.info("Evicted stale driver {} from matching (heartbeat {} ms old)",
                                driverId, now - lastBeat);
                        continue;
                    }
                } catch (NumberFormatException e) {
                    redisTemplate.opsForGeo().remove(GEO_KEY, driverId);
                    log.info("Evicted driver {} from matching (invalid heartbeat)", driverId);
                    continue;
                }

                double distKm = result.getDistance().getValue();
                Point coords = result.getContent().getPoint();

                Map<String, String> metaMap = new HashMap<>();
                details.forEach((k, v) -> metaMap.put(k.toString(), v.toString()));

                nearby.add(new DriverLocation(driverId, coords.getY(), coords.getX(),
                        distKm, metaMap));
            }
        }
        return nearby;
    }

    public Optional<DriverLocation> getDriverLocation(String driverId) {
        List<Point> positions = redisTemplate.opsForGeo().position(GEO_KEY, driverId);
        if (positions == null || positions.isEmpty() || positions.get(0) == null) {
            return Optional.empty();
        }
        Point point = positions.get(0);
        Map<Object, Object> details = redisTemplate.opsForHash()
                .entries(DRIVER_HASH_KEY_PREFIX + driverId);
        Map<String, String> metaMap = new HashMap<>();
        details.forEach((k, v) -> metaMap.put(k.toString(), v.toString()));

        return Optional.of(new DriverLocation(driverId, point.getY(), point.getX(), 0, metaMap));
    }

    public void removeDriverLocation(String driverId) {
        redisTemplate.opsForGeo().remove(GEO_KEY, driverId);
        redisTemplate.delete(DRIVER_HASH_KEY_PREFIX + driverId);
        redisTemplate.delete(DRIVER_RIDE_KEY_PREFIX + driverId);
        log.info("Driver location removed from Redis: {}", driverId);
    }

    public long getActiveDriverCount() {
        Long count = redisTemplate.opsForZSet().zCard(GEO_KEY);
        return count != null ? count : 0L;
    }

    public record DriverLocation(String driverId, double latitude, double longitude,
                                  double distanceKm, Map<String, String> metadata) {}
}
