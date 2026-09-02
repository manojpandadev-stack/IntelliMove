package com.intellimove.user.service;

import com.intellimove.common.dto.PagedResponse;
import com.intellimove.common.enums.Role;
import com.intellimove.common.event.UserRegisteredEvent;
import com.intellimove.common.exception.BusinessException;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.user.dto.CreateUserRequest;
import com.intellimove.user.dto.UpdateUserRequest;
import com.intellimove.user.dto.UserResponse;
import com.intellimove.user.entity.User;
import com.intellimove.user.entity.UserProfilePhoto;
import com.intellimove.user.mapper.UserMapper;
import com.intellimove.user.repository.UserProfilePhotoRepository;
import com.intellimove.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserProfilePhotoRepository photoRepository;
    private final UserProvisioningWriter provisioningWriter;

    /** Avatars are small: 2 MB is generous for a 1024px profile picture. */
    public static final long MAX_PHOTO_BYTES = 2L * 1024 * 1024;

    /**
     * Stores a validated avatar image keyed by the JWT-verified user id.
     *
     * Content validation NEVER trusts filename or declared MIME type:
     * magic bytes decide. JPEG (FF D8 FF), PNG (89 50 4E 47 …) and
     * WebP (RIFF…WEBP) are accepted; everything else is rejected.
     *
     * NOTE: deliberately independent of the users table — the profile row is
     * provisioned asynchronously by the auth-service USER_REGISTERED outbox
     * event (may not exist yet), and a profile photo must work for every
     * authenticated identity.
     */
    @Transactional
    public void uploadProfilePhoto(UUID userId, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("PHOTO_EMPTY", "The selected file is empty.");
        }
        if (bytes.length > MAX_PHOTO_BYTES) {
            throw new BusinessException("PHOTO_TOO_LARGE", "Photo must be 2 MB or smaller.");
        }
        String contentType = detectImageContentType(bytes);
        if (contentType == null) {
            throw new BusinessException(
                    "PHOTO_UNSUPPORTED", "Only JPG, PNG or WebP images are supported.");
        }
        // File signature alone is not enough — truncated or tampered files can
        // carry valid magic bytes. JPEG/PNG must actually DECODE; WebP has no
        // standard JDK decoder so its stricter RIFF/WEBP signature check stands.
        if (!"image/webp".equals(contentType)) {
            try {
                if (javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes)) == null) {
                    throw new BusinessException(
                            "PHOTO_CORRUPT", "The image file appears to be corrupted.");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(
                        "PHOTO_CORRUPT", "The image file appears to be corrupted.");
            }
        }

        UserProfilePhoto photo = photoRepository.findByUserId(userId).orElseGet(() ->
                UserProfilePhoto.builder().userId(userId).build());
        photo.setContentType(contentType);
        photo.setData(bytes);
        photo.setByteSize(bytes.length);
        photo.setUpdatedAt(Instant.now());
        photoRepository.save(photo);
        log.info("Profile photo updated for user {}", userId);
    }

    /** Removes the stored photo (if any). */
    @Transactional
    public void removeProfilePhoto(UUID userId) {
        photoRepository.findByUserId(userId).ifPresent(photoRepository::delete);
        log.info("Profile photo removed for user {}", userId);
    }

    @Transactional(readOnly = true)
    public Optional<UserProfilePhoto> getProfilePhoto(UUID userId) {
        return photoRepository.findByUserId(userId);
    }

    /**
     * Detects the real image type from file-signature bytes.
     * Returns the canonical content type, or null when unrecognized.
     */
    private String detectImageContentType(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == '\r' && b[5] == '\n' && (b[6] & 0xFF) == 0x1A && b[7] == '\n') {
            return "image/png";
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("EMAIL_EXISTS", "Email already registered");
        }

        User user = userMapper.toEntity(request);
        user.setEmail(request.getEmail());
        user.setRole(request.getRole() != null ? request.getRole() : Role.CUSTOMER);
        user = userRepository.save(user);
        log.info("User created: {}", user.getEmail());
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return userMapper.toResponse(user);
    }

    /**
     * Idempotent provisioning of the user profile from a USER_REGISTERED event
     * (auth service → transactional outbox → Kafka → {this}).
     *
     * The profile row uses the auth-service user id as its primary key so
     * identities line up across services. Safe under duplicate delivery,
     * outbox retries and Kafka replays: already-provisioned ids and emails are
     * left untouched, and a concurrent insert that races this one is isolated
     * in its own REQUIRES_NEW transaction (see insertProvisionedUser) whose
     * collision rollback never poisons the listener's outer transaction.
     */
    @Transactional
    public void provisionUser(UserRegisteredEvent event) {
        if (event.getUserId() == null || event.getUserId().isBlank()) {
            log.warn("UserRegisteredEvent without userId, skipping (eventId={})", event.getEventId());
            return;
        }
        UUID userId;
        try {
            userId = UUID.fromString(event.getUserId());
        } catch (IllegalArgumentException ex) {
            log.warn("UserRegisteredEvent with non-UUID userId '{}', skipping", event.getUserId());
            return;
        }
        if (event.getEmail() == null || event.getEmail().isBlank()
                || event.getFirstName() == null || event.getFirstName().isBlank()
                || event.getLastName() == null || event.getLastName().isBlank()) {
            log.warn("UserRegisteredEvent missing required profile fields, skipping (userId={})", userId);
            return;
        }

        // Idempotency guard #1 — already provisioned (duplicate/replay).
        if (userRepository.findById(userId).isPresent()) {
            log.info("User {} already provisioned — ignoring duplicate provisioning event", userId);
            return;
        }
        // Idempotency guard #2 — email owned elsewhere (avoid unique-constraint crash-loop).
        if (userRepository.existsByEmail(event.getEmail())) {
            log.warn("Email {} already exists in user-service under another id — skipping provisioning (userId={})",
                    event.getEmail(), userId);
            return;
        }
        Role role = parseProvisionedRole(event.getRole(), userId);

        // The insert runs in its own REQUIRES_NEW transaction on a separate bean
        // (UserProvisioningWriter) so that a concurrent-delivery INSERT /
        // unique-key collision rolls back only that inner transaction. The prior
        // pattern (catching DataIntegrityViolationException inline within the same
        // @Transactional method — and even a @Transactional(REQUIRES_NEW) on a
        // private self-invoked method, which Spring AOP silently ignores) left the
        // outer session rollback-only, making the listener's commit throw
        // UnexpectedRollbackException -> Kafka retry exhaustion -> poison message
        // -> profile never observed via GET /users/{id}.
        try {
            provisioningWriter.insertProvisionedUser(userId, event, role);
        } catch (DataIntegrityViolationException ex) {
            // Idempotency guard #3 — a concurrent delivery committed first.
            // Log the specific cause so a genuine constraint violation (not a race)
            // is diagnosable instead of silently reporting "provisioned concurrently".
            log.warn("Provisioning insert failed for {} — cause: {}", userId,
                    ex.getMostSpecificCause().getMessage());
            if (userRepository.findById(userId).isPresent()) {
                log.info("User {} was provisioned concurrently — treating duplicate delivery as success", userId);
            }
        }
    }

    private Role parseProvisionedRole(String role, UUID userId) {
        if (role == null || role.isBlank()) {
            return Role.CUSTOMER;
        }
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown role '{}' in UserRegisteredEvent for {}, defaulting to CUSTOMER", role, userId);
            return Role.CUSTOMER;
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        userMapper.updateEntity(request, user);
        user = userRepository.save(user);
        log.info("User updated: {}", user.getEmail());
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getAllUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = userRepository.findAll(pageable);
        return toPagedResponse(users);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> searchUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = userRepository.searchUsers(query, pageable);
        return toPagedResponse(users);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getUsersByRole(Role role, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = userRepository.findByRole(role, pageable);
        return toPagedResponse(users);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        userRepository.delete(user);
        log.info("User deleted: {}", user.getEmail());
    }

    private PagedResponse<UserResponse> toPagedResponse(Page<User> page) {
        return PagedResponse.<UserResponse>builder()
                .content(page.getContent().stream().map(userMapper::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
