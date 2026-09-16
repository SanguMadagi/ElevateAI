package com.ai.interview.config;

import com.ai.interview.model.Profile;
import com.ai.interview.model.Result;
import com.ai.interview.model.Submission;
import com.ai.interview.model.TestSession;
import com.ai.interview.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        ensureIndexes();
    }

    private void ensureIndexes() {
        try {
            log.info("Ensuring database indexes...");

            // 1. Users index
            mongoTemplate.indexOps(User.class).ensureIndex(
                new Index().on("email", Sort.Direction.ASC).unique()
            );

            // 2. Profiles index
            mongoTemplate.indexOps(Profile.class).ensureIndex(
                new Index().on("userId", Sort.Direction.ASC).unique()
            );

            // 3. Test Sessions index
            mongoTemplate.indexOps(TestSession.class).ensureIndex(
                new Index().on("userId", Sort.Direction.ASC)
            );
            mongoTemplate.indexOps(TestSession.class).ensureIndex(
                new Index().on("testId", Sort.Direction.ASC).unique()
            );

            // 4. Submissions index
            mongoTemplate.indexOps(Submission.class).ensureIndex(
                new Index().on("testId", Sort.Direction.ASC).unique()
            );
            mongoTemplate.indexOps(Submission.class).ensureIndex(
                new Index().on("userId", Sort.Direction.ASC)
            );

            // 5. Results index
            mongoTemplate.indexOps(Result.class).ensureIndex(
                new Index().on("testId", Sort.Direction.ASC).unique()
            );
            mongoTemplate.indexOps(Result.class).ensureIndex(
                new Index().on("userId", Sort.Direction.ASC)
            );

            log.info("Database indexes ensured successfully.");
        } catch (Exception e) {
            log.error("Failed to ensure indexes: {}", e.getMessage());
        }
    }
}
