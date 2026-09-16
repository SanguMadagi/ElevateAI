package com.ai.interview.repository;

import com.ai.interview.model.Result;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ResultRepository extends MongoRepository<Result, String> {
    Optional<Result> findByTestId(String testId);
    List<Result> findByUserId(String userId);
}
