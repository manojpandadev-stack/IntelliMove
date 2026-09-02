package com.intellimove.user.repository;

import com.intellimove.user.entity.SavedPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavedPlaceRepository extends JpaRepository<SavedPlace, UUID> {

    List<SavedPlace> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
