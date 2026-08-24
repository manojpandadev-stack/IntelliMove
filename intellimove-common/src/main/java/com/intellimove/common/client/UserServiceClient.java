package com.intellimove.common.client;

import com.intellimove.common.dto.user.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Client for synchronous inter-service communication with User Service.
 * Uses RestTemplate; could be upgraded to WebClient or OpenFeign.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${service.user.url:http://localhost:8082}")
    private String userServiceUrl;

    public Optional<UserDTO> getUserById(UUID userId, String bearerToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(bearerToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<UserDTO> response = restTemplate.exchange(
                    userServiceUrl + "/api/v1/users/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserDTO.class);
            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.error("Failed to get user {} from user service: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<UserDTO> getUserByEmail(String email, String bearerToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(bearerToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<UserDTO> response = restTemplate.exchange(
                    userServiceUrl + "/api/v1/users/email/" + email,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserDTO.class);
            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.error("Failed to get user by email from user service: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
