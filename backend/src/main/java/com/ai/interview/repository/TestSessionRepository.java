package com.ai.interview.repository;

import com.ai.interview.model.TestSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface TestSessionRepository extends MongoRepository<TestSession, String> {
    List<TestSession> findByUserIdOrderByCreatedAtDesc(String userId);
    List<TestSession> findByUserId(String userId);
}
