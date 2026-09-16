package com.ai.interview.repository;

import com.ai.interview.model.Submission;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface SubmissionRepository extends MongoRepository<Submission, String> {
    Optional<Submission> findByTestId(String testId);
    Optional<Submission> findFirstByTestId(String testId);
    java.util.List<Submission> findByUserId(String userId);
}
