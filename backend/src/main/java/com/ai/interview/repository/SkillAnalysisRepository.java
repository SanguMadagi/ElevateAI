package com.ai.interview.repository;

import com.ai.interview.model.SkillAnalysis;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillAnalysisRepository extends MongoRepository<SkillAnalysis, String> {
    Optional<SkillAnalysis> findFirstByUserIdOrderByUpdatedAtDesc(String userId);
    List<SkillAnalysis> findAllByUserId(String userId);
    void deleteByUserId(String userId);
}

