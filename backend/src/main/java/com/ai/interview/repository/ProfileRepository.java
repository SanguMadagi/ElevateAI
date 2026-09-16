package com.ai.interview.repository;

import com.ai.interview.model.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProfileRepository extends MongoRepository<Profile, String> {
    Optional<Profile> findByUserId(String userId);
    Optional<Profile> findFirstByOrderByUpdatedAtDesc();
}
