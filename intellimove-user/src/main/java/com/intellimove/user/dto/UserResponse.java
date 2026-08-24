package com.intellimove.user.dto;

import com.intellimove.common.enums.Role;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private Role role;
    private boolean enabled;
    private String profileImageUrl;
    private String address;
    private String city;
    private Instant createdAt;
    private Instant updatedAt;
}
