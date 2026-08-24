package com.intellimove.common.dto.user;

import com.intellimove.common.enums.Role;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private Role role;
    private boolean enabled;
    private Instant createdAt;
}
