package com.intellimove.user.repository;

import com.intellimove.user.entity.UserProfilePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfilePhotoRepository extends JpaRepository<UserProfilePhoto, UUID> {

    Optional<UserProfilePhoto> findByUserId(UUID userId);
}
