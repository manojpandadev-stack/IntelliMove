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
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
                String driverId = result.getContent().getName();
                Map<Object, Object> details = redisTemplate.opsForHash()
                        .entries(DRIVER_HASH_KEY_PREFIX + driverId);

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
