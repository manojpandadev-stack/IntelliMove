package com.intellimove.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Published by the Auth Service after a successful self-registration so the
 * User Service can provision the matching user profile (idempotently).
 *
 * The user id (the Auth User's UUID) is the business key: the profile row in
 * the user service uses exactly the same id, keeping identities aligned across
 * services without any direct database coupling.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent extends DomainEvent {

    /** Auth-service user id (UUID) — also used as the user-service profile id. */
    private String userId;

    /** Primary email used for registration. */
    private String email;

    private String firstName;

    private String lastName;

    private String phoneNumber;

    /** Primary role name (CUSTOMER, DRIVER, ADMIN, ...). */
    private String role;

    private boolean enabled;
}