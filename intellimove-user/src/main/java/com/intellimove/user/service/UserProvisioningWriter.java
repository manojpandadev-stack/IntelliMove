package com.intellimove.user.service;

import com.intellimove.common.event.UserRegisteredEvent;
import com.intellimove.common.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.UUID;

/**
 * Isolated writer for Kafka-driven user provisioning.
 *
 * The insert lives in its own REQUIRES_NEW transaction on a SEPARATE bean —
 * a @Transactional annotation on a private method invoked via `this` inside
 * UserService is silently ignored by Spring AOP (self-invocation), so a caught
 * DataIntegrityViolationException used to leave the OUTER provisionUser
 * transaction rollback-only, making the listener commit throw
 * UnexpectedRollbackException and burn all Kafka retries (poison message).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserProvisioningWriter {

    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertProvisionedUser(UUID userId, UserRegisteredEvent event, Role role) {
        // The profile id IS the auth-service user id: identity is correlated across
        // services by this value, so it MUST be inserted verbatim.
        //
        // Why a native INSERT instead of entityManager.persist()/merge() or
        // userRepository.save()? The `users` table maps a @GeneratedValue UUID id:
        //   * persist with id set, @Version null  -> Hibernate 6 rejects the
        //     pre-assigned id ("detached entity ... uninitialized version 'null'").
        //   * persist with id set, @Version 0     -> EntityExistsException
        //     "detached entity passed to persist" (id+version => classified detached).
        //   * merge                                -> Hibernate re-generates a NEW UUID
        //     for the INSERT, silently breaking cross-service identity correlation
        //     (profile lookup by the JWT userId then 404s forever).
        // A native INSERT is the only deterministic way to persist an externally
        // assigned id into a generated-id table. created_at/updated_at use the DB
        // defaults (now()), version the column default (0).
        //
        // A concurrent duplicate delivery still collides on the PRIMARY KEY and
        // surfaces as DataIntegrityViolationException, handled by the caller.
        entityManager.createNativeQuery("""
                INSERT INTO users (id, version, email, first_name, last_name, phone_number, role, enabled)
                VALUES (CAST(:id AS uuid), 0, :email, :firstName, :lastName, CAST(:phoneNumber AS varchar), CAST(:role AS varchar), :enabled)
                """)
                .setParameter("id", userId.toString())
                .setParameter("email", event.getEmail())
                .setParameter("firstName", event.getFirstName())
                .setParameter("lastName", event.getLastName())
                .setParameter("phoneNumber", event.getPhoneNumber())
                .setParameter("role", role.name())
                .setParameter("enabled", event.isEnabled())
                .executeUpdate();
        log.info("User profile provisioned: id={}, email={}, role={}", userId, event.getEmail(), role);
    }
}
