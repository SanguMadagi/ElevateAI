package com.ai.interview.service;

import com.ai.interview.model.*;
import com.ai.interview.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareerWorkspaceService {

    private final ProfileRepository profileRepository;
    private final SkillAnalysisRepository skillAnalysisRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final UserRepository userRepository;
    private final AiService aiService;

    @Value("${app.workspace.base-dir:career_workspaces}")
    private String baseWorkspaceDir;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Map<String, Object> generateWorkspace(String userId) {
        Profile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null) {
            profile = profileRepository.findFirstByOrderByUpdatedAtDesc().orElse(null);
        }
        User userObj = userRepository.findById(userId)
                .or(() -> userRepository.findByEmail(userId))
                .orElse(null);

        SkillAnalysis analysis = skillAnalysisRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId).orElse(null);

        String candidateName = (profile != null && profile.getName() != null && !profile.getName().isBlank())
                ? profile.getName()
                : (userObj != null && userObj.getName() != null && !userObj.getName().isBlank() ? userObj.getName() : "Candidate");

        String targetRole = (profile != null && profile.getTargetRole() != null && !profile.getTargetRole().isBlank())
                ? profile.getTargetRole()
                : "Software Engineer";

        String skillsList = (profile != null && profile.getSkills() != null && !profile.getSkills().isEmpty())
                ? String.join(", ", profile.getSkills())
                : "Java, Spring Boot, React, SQL, REST APIs, Git";

        String projectsList = (profile != null && profile.getProjects() != null && !profile.getProjects().isEmpty())
                ? String.join(", ", profile.getProjects())
                : (profile != null && profile.getResumeProjects() != null && !profile.getResumeProjects().isEmpty()
                    ? String.join(", ", profile.getResumeProjects())
                    : "Enterprise Web Applications & Microservices");

        Path userWorkspacePath = userWorkspacePath(userId);
        List<String> createdFiles = new ArrayList<>();

        try {
            Files.createDirectories(userWorkspacePath.resolve("Resume"));
            Files.createDirectories(userWorkspacePath.resolve("Interview"));
            Files.createDirectories(userWorkspacePath.resolve("Preparation"));
            Files.createDirectories(userWorkspacePath.resolve("Progress"));

            String nowStr = LocalDateTime.now().format(DATE_FORMATTER);

            // =========================================================
            // 1. Resume/Resume-Analysis.md (Sanitized with NO placeholders)
            // =========================================================
            String resumeSynthesis;
            try {
                String resumePrompt = String.format(
                    "You are a principal engineering hiring manager.\n" +
                    "Generate an in-depth, concrete technical resume analysis for:\n" +
                    "- Candidate Name: %s\n" +
                    "- Target Role: %s\n" +
                    "- Skills: %s\n" +
                    "- Projects: %s\n\n" +
                    "CRITICAL ANTI-PLACEHOLDER INSTRUCTIONS:\n" +
                    "- DO NOT output dummy resume headers or contact information (no [Candidate Name], [City, State], [Email Address], [LinkedIn Profile], [Portfolio Link], or bracketed placeholders).\n" +
                    "- Start directly with section 1.\n" +
                    "- Include sections:\n" +
                    "  ## 1. Executive Technical Summary\n" +
                    "  ## 2. Core Strengths & Demonstrated Competency\n" +
                    "  ## 3. Architecture & Project Depth Analysis\n" +
                    "  ## 4. High-Impact Target Role Recommendations\n",
                    candidateName, targetRole, skillsList, projectsList
                );
                resumeSynthesis = aiService.generateContent(resumePrompt);
                resumeSynthesis = cleanPlaceholders(resumeSynthesis, candidateName, targetRole);
            } catch (Exception e) {
                log.warn("AI generation failed for resume analysis, using structured template: {}", e.getMessage());
                resumeSynthesis = String.format(
                    "## 1. Executive Technical Summary\n\n" +
                    "%s demonstrates strong technical acumen targeting **%s** roles. " +
                    "Core competencies span %s, backed by hands-on engineering in %s.\n\n" +
                    "## 2. Core Strengths & Demonstrated Competency\n\n" +
                    "- **Backend Engineering**: Robust API architecture, transaction management, and service decomposition.\n" +
                    "- **Data & Storage**: Relational schema design, query optimization, and structured persistence.\n" +
                    "- **Modern Frontend**: Component lifecycles, state management, and responsive styling.\n\n" +
                    "## 3. Architecture & Project Depth Analysis\n\n" +
                    "Demonstrated project work in **%s** proves practical application of layered architecture, " +
                    "separation of concerns, and clean code principles.\n\n" +
                    "## 4. High-Impact Target Role Recommendations\n\n" +
                    "- Emphasize end-to-end integration tests and containerized deployments.\n" +
                    "- Prepare deep-dive explanations on concurrency, database indexing, and caching trade-offs.\n",
                    candidateName, targetRole, skillsList, projectsList, projectsList
                );
            }

            String resumeMd = "# AI Resume Analysis & Architecture Breakdown\n\n" +
                    "**Candidate**: " + candidateName + "\n" +
                    "**Target Role**: " + targetRole + "\n" +
                    "**Analysis Date**: " + nowStr + "\n\n" +
                    "---\n\n" +
                    resumeSynthesis + "\n";
            writeFileSafely(userWorkspacePath.resolve("Resume/Resume-Analysis.md"), resumeMd);
            createdFiles.add("Resume/Resume-Analysis.md");

            // =========================================================
            // 2. Resume/ATS-Optimization-Checklist.md
            // =========================================================
            String atsContent = String.format(
                "# ATS Keyword Optimization & Resume Polish\n\n" +
                "**Candidate**: %s\n" +
                "**Target Role**: %s\n" +
                "**Generated at**: %s\n\n" +
                "---\n\n" +
                "## 1. Role-Specific Keyword Match Analysis\n\n" +
                "Applicant Tracking Systems (ATS) scan for exact technical keywords before human review. " +
                "Ensure your resume explicitly mentions these high-priority industry terms:\n\n" +
                "| Category | Verified Skills | Recommended High-Priority Keywords to Add |\n" +
                "| :--- | :--- | :--- |\n" +
                "| **Backend / Languages** | %s | Clean Architecture, Concurrency, Multithreading, Garbage Collection |\n" +
                "| **Frameworks / APIs** | Spring Boot, REST | Spring Security, JWT Authentication, Microservices, Hibernate / JPA |\n" +
                "| **Databases / Caching** | SQL, RDBMS | Redis Cache-Aside, Query Optimization, Database Indexing, Connection Pooling |\n" +
                "| **DevOps & Testing** | Git | Docker, CI/CD Pipelines, JUnit 5, Mockito, Postman |\n\n" +
                "## 2. Impact Bullet Formula (Google XYZ Method)\n\n" +
                "Transform standard duty descriptions into high-scoring accomplishment statements using the formula:\n" +
                "> *\"Accomplished [X] as measured by [Y], by doing [Z]\"*\n\n" +
                "### Example Transformations:\n" +
                "- ❌ *\"Built REST APIs for user authentication.\"*\n" +
                "- ✅ **\"Architected stateless RESTful authentication services using Spring Boot and JWT, reducing authorization latency by 35%% and securing endpoints for 10,000+ simulated requests.\"**\n\n" +
                "- ❌ *\"Worked on database queries and performance.\"*\n" +
                "- ✅ **\"Optimized SQL queries and added strategic composite indexes, eliminating N+1 query bottlenecks and accelerating database response times by 40%%.\"**\n\n" +
                "## 3. Pre-Submission Checklist\n\n" +
                "- [ ] Single-column layout (ATS-friendly, no complex multi-column tables or text boxes)\n" +
                "- [ ] Standard font (Calibri, Arial, or Inter) with clean hierarchy\n" +
                "- [ ] PDF format with selectable, copyable text (not an image or flattened scan)\n" +
                "- [ ] Every technical claim matched by verifiable code in your GitHub repository\n",
                candidateName, targetRole, nowStr, skillsList
            );
            writeFileSafely(userWorkspacePath.resolve("Resume/ATS-Optimization-Checklist.md"), atsContent);
            createdFiles.add("Resume/ATS-Optimization-Checklist.md");

            // =========================================================
            // 3. Interview/Weak-Topics.md (Fixed String.format bug & placeholders)
            // =========================================================
            String analysisSource = (analysis != null && analysis.getOverallReadinessScore() > 0)
                    ? "Verified assessment & mock interview evaluations"
                    : "Baseline evaluation (Complete an assessment to populate live score vectors)";
            String readinessStr = (analysis != null && analysis.getOverallReadinessScore() > 0)
                    ? String.format(Locale.ROOT, "%.1f%%", analysis.getOverallReadinessScore())
                    : "Pending Assessment";

            List<String> weakConcepts = (analysis != null && analysis.getWeakConcepts() != null && !analysis.getWeakConcepts().isEmpty())
                    ? analysis.getWeakConcepts()
                    : List.of(
                        "Java Multithreading & Synchronization (volatile, synchronized, ReentrantLock)",
                        "JPA / Hibernate N+1 Query Problem & FetchType.LAZY optimization",
                        "Spring Security Filter Chain & JWT Token Validation",
                        "SQL Indexing & Execution Plans (B-Tree, composite indexes, query cost)"
                    );

            List<String> gaps = (analysis != null && analysis.getGapDetections() != null && !analysis.getGapDetections().isEmpty())
                    ? analysis.getGapDetections()
                    : List.of(
                        "High-level resume claim vs depth in concurrency mechanisms",
                        "Architectural trade-off justification between monolithic and microservice approaches"
                    );

            StringBuilder weakListSb = new StringBuilder();
            for (String wc : weakConcepts) {
                weakListSb.append("- **").append(wc).append("**\n");
            }

            StringBuilder gapsSb = new StringBuilder();
            for (String g : gaps) {
                gapsSb.append("- ").append(g).append("\n");
            }

            String weakContent = String.format(
                "# Targeted Technical Weaknesses & Concept Mastery\n\n" +
                "**Candidate**: %s\n" +
                "**Evidence Source**: %s\n" +
                "**Generated at**: %s\n\n" +
                "**Overall Readiness Index**: %s\n\n" +
                "---\n\n" +
                "## 1. High-Priority Weak Concepts to Resolve\n\n%s\n" +
                "## 2. Knowledge Gap Detections\n\n%s\n" +
                "## 3. Recommended Action Plan\n\n" +
                "%s\n",
                candidateName,
                analysisSource,
                nowStr,
                readinessStr,
                weakListSb.toString(),
                gapsSb.toString(),
                (analysis != null && analysis.getRecommendedNextAction() != null && !analysis.getRecommendedNextAction().isBlank())
                    ? analysis.getRecommendedNextAction()
                    : "Review core Java concurrency models and SQL execution plans, then launch an AI Mock Interview to verify score improvement."
            );
            writeFileSafely(userWorkspacePath.resolve("Interview/Weak-Topics.md"), weakContent);
            createdFiles.add("Interview/Weak-Topics.md");

            // =========================================================
            // 4. Interview/Resume-Questions.md (Tailored to projects & sanitized)
            // =========================================================
            String questionsBody;
            try {
                String questionsPrompt = String.format(
                    "You are a senior technical interviewer.\n" +
                    "Generate 5 challenging, realistic technical interview questions for candidate %s targeting %s.\n" +
                    "Base the questions on their demonstrated skills (%s) and projects (%s).\n" +
                    "Provide a crisp explanation of what an interviewer evaluates for each question.\n\n" +
                    "CRITICAL: DO NOT use placeholders like [Project Name], [Company], or [Candidate]. Write concrete questions tailored to their actual stack.\n",
                    candidateName, targetRole, skillsList, projectsList
                );
                questionsBody = aiService.generateContent(questionsPrompt);
                questionsBody = cleanPlaceholders(questionsBody, candidateName, targetRole);
            } catch (Exception e) {
                questionsBody = String.format(
                    "### 1. In your project (%s), how did you design the database schema and handle transactional consistency?\n" +
                    "**Interviewer Evaluation**: Checks understanding of ACID properties, `@Transactional` boundaries, and isolation levels.\n\n" +
                    "### 2. How does the Spring Boot application handle concurrent requests without race conditions?\n" +
                    "**Interviewer Evaluation**: Tests grasp of Servlet thread pool (Tomcat), stateless controller design, and thread-safe bean scoping.\n\n" +
                    "### 3. How do you identify and fix the N+1 query problem in Spring Data JPA?\n" +
                    "**Interviewer Evaluation**: Evaluates knowledge of `@EntityGraph`, `JOIN FETCH`, and Hibernate SQL log analysis.\n\n" +
                    "### 4. What caching strategy would you implement if your read throughput spikes 10x?\n" +
                    "**Interviewer Evaluation**: Tests cache-aside pattern using Redis, TTL expiration, and cache invalidation strategies.\n\n" +
                    "### 5. How are security and authentication enforced across your REST endpoints?\n" +
                    "**Interviewer Evaluation**: Tests familiarity with JWT token signing, filter chains, and role-based access control.\n",
                    projectsList
                );
            }

            String questionsMd = "# Targeted Resume & Project Interview Questions\n\n" +
                    "**Candidate**: " + candidateName + "\n" +
                    "**Target Role**: " + targetRole + "\n" +
                    "**Generated at**: " + nowStr + "\n\n" +
                    "---\n\n" +
                    questionsBody + "\n";
            writeFileSafely(userWorkspacePath.resolve("Interview/Resume-Questions.md"), questionsMd);
            createdFiles.add("Interview/Resume-Questions.md");

            // =========================================================
            // 5. Interview/System-Design-CheatSheet.md
            // =========================================================
            String systemDesignContent = String.format(
                "# System Design & Architecture Playbook\n\n" +
                "**Candidate**: %s\n" +
                "**Target Role**: %s\n" +
                "**Generated at**: %s\n\n" +
                "---\n\n" +
                "## 1. Core Architectural Patterns\n\n" +
                "### A. Caching Strategies (Redis)\n" +
                "- **Cache-Aside (Lazy Loading)**: Application queries Redis first. On miss, queries DB, writes to Redis, returns data. Best for read-heavy workloads.\n" +
                "- **Write-Through**: Application writes to cache, cache writes to DB synchronously. Prevents stale data.\n" +
                "- **Cache Invalidation**: Always define a reasonable TTL (Time-To-Live) and evict keys on entity update.\n\n" +
                "### B. Asynchronous Messaging & Decoupling (Kafka / RabbitMQ)\n" +
                "- **Event-Driven Architecture**: Decouple producer and consumer services to prevent cascading failures.\n" +
                "- **At-Least-Once Delivery**: Consumers must be idempotent (e.g. deduplication via unique transaction IDs).\n\n" +
                "### C. Database Scaling & Indexing\n" +
                "- **Composite Indexes**: Always match the leftmost prefix of query WHERE clauses.\n" +
                "- **Read Replicas**: Route read queries (`SELECT`) to replicas and write queries (`INSERT/UPDATE`) to primary master.\n\n" +
                "### D. Security & Resiliency\n" +
                "- **Stateless Authentication**: JWT tokens with asymmetric signing (RS256) or HMAC-SHA256.\n" +
                "- **Circuit Breaker (Resilience4j)**: Automatically trip open when downstream service failure rate exceeds threshold (e.g. 50%%), preventing thread starvation.\n",
                candidateName, targetRole, nowStr
            );
            writeFileSafely(userWorkspacePath.resolve("Interview/System-Design-CheatSheet.md"), systemDesignContent);
            createdFiles.add("Interview/System-Design-CheatSheet.md");

            // =========================================================
            // 6. Preparation/30-Day-Roadmap.md
            // =========================================================
            String roadmapContent = String.format(
                "# 30-Day Mastery & Interview Preparation Roadmap\n\n" +
                "**Candidate**: %s\n" +
                "**Target Role**: %s\n" +
                "**Generated at**: %s\n\n" +
                "---\n\n" +
                "## Week 1: Core Fundamentals & Data Structures\n\n" +
                "- **Day 1-2**: Java 8-21 Features — Lambdas, Streams API, Optional, Records, Virtual Threads.\n" +
                "- **Day 3-4**: Memory Model & Concurrency — volatile, synchronized, ConcurrentHashMap, ExecutorService.\n" +
                "- **Day 5-7**: Top Interview DSA — Two Pointers, Sliding Window, Fast/Slow pointers, Binary Search, DFS/BFS.\n\n" +
                "## Week 2: Framework Mastery (Spring Boot & REST Architecture)\n\n" +
                "- **Day 8-10**: Spring Core & Lifecycle — Dependency Injection, Bean Scopes, Component Scanning, AOP.\n" +
                "- **Day 11-12**: Spring Data JPA & Performance — `@EntityGraph`, solving N+1 queries, indexing, pagination.\n" +
                "- **Day 13-14**: Spring Security 6 & JWT — SecurityFilterChain, OncePerRequestFilter, CORS configuration.\n\n" +
                "## Week 3: Databases & Distributed Architecture\n\n" +
                "- **Day 15-17**: SQL & Database Optimization — EXPLAIN queries, B-Tree indexes, transactions & isolation levels.\n" +
                "- **Day 18-20**: Caching & Messaging — Redis cache-aside implementation, Kafka topics, consumer offset management.\n" +
                "- **Day 21**: Microservices Design — API Gateway, Service Discovery, Centralized Config, Circuit Breaker.\n\n" +
                "## Week 4: Full-Scale Mock Drills & Interview Polish\n\n" +
                "- **Day 22-24**: Live AI Mock Interviews — Practice verbal voice responses under timed proctored conditions.\n" +
                "- **Day 25-27**: Project Cross-Examination — Master STAR-method walkthroughs for every bullet point on your resume.\n" +
                "- **Day 28-30**: Final Simulation & Confidence Drills — Review Weak-Topics.md and verify all scores > 85%%.\n",
                candidateName, targetRole, nowStr
            );
            writeFileSafely(userWorkspacePath.resolve("Preparation/30-Day-Roadmap.md"), roadmapContent);
            createdFiles.add("Preparation/30-Day-Roadmap.md");

            // =========================================================
            // 7. Progress/Skill-Progress.md
            // =========================================================
            StringBuilder scoreTableSb = new StringBuilder();
            scoreTableSb.append("| Skill / Category | Measured Score | Status |\n");
            scoreTableSb.append("| :--- | :--- | :--- |\n");

            if (analysis != null && analysis.getSkillScores() != null && !analysis.getSkillScores().isEmpty()) {
                analysis.getSkillScores().forEach((skill, score) -> {
                    String status = score >= 80 ? "✅ Verified Proficient" : (score >= 60 ? "⚠️ Needs Practice" : "❌ Critical Focus");
                    scoreTableSb.append(String.format(Locale.ROOT, "| %s | %.1f%% | %s |\n", skill, score, status));
                });
            } else {
                scoreTableSb.append("| Core Java | 78.0% | ⚠️ Baseline |\n");
                scoreTableSb.append("| Spring Boot | 75.0% | ⚠️ Baseline |\n");
                scoreTableSb.append("| REST APIs | 82.0% | ✅ Baseline |\n");
                scoreTableSb.append("| SQL & Persistence | 72.0% | ⚠️ Baseline |\n");
            }

            String progressContent = String.format(
                "# Candidate Skill Progress Tracker\n\n" +
                "**Candidate**: %s\n" +
                "**Target Role**: %s\n" +
                "**Last Updated**: %s\n" +
                "**Overall Readiness Index**: %s\n\n" +
                "---\n\n" +
                "## Verified Competency Scores\n\n%s\n\n" +
                "## Progression Milestones\n\n" +
                "- **Stage 1**: Profile & Resume Architecture Synthesis (Completed)\n" +
                "- **Stage 2**: Core Technical Assessment Verification\n" +
                "- **Stage 3**: Adaptive AI Mock Interview Simulation\n" +
                "- **Stage 4**: Target Readiness > 85%% reached\n",
                candidateName,
                targetRole,
                nowStr,
                readinessStr,
                scoreTableSb.toString()
            );
            writeFileSafely(userWorkspacePath.resolve("Progress/Skill-Progress.md"), progressContent);
            createdFiles.add("Progress/Skill-Progress.md");

        } catch (Exception e) {
            log.error("Failed to generate workspace for user {}: {}", userId, e.getMessage(), e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("workspacePath", userWorkspacePath.toString());
        result.put("generatedFiles", createdFiles);
        result.put("timestamp", LocalDateTime.now());
        return result;
    }

    public Map<String, Object> generateCustomFile(String userId, String fileName, String folder, String prompt) {
        if (fileName == null || fileName.trim().isBlank()) {
            throw new IllegalArgumentException("File name is required.");
        }
        if (prompt == null || prompt.trim().isBlank()) {
            throw new IllegalArgumentException("Prompt or instructions are required.");
        }

        // Clean folder (prevent traversal, default to 'Custom')
        String safeFolder = (folder == null || folder.trim().isBlank()) ? "Custom" : folder.trim().replaceAll("[^a-zA-Z0-9_-]", "");
        if (safeFolder.isBlank()) safeFolder = "Custom";

        // Clean fileName (ensure .md, prevent traversal)
        String rawName = fileName.trim().replaceAll("[^a-zA-Z0-9._-]", "-");
        if (!rawName.toLowerCase().endsWith(".md")) {
            rawName += ".md";
        }
        rawName = rawName.replaceAll("^[.-]+", "");
        if (rawName.equals(".md") || rawName.isBlank()) {
            rawName = "Custom-Guide-" + System.currentTimeMillis() + ".md";
        }

        Profile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null) {
            profile = profileRepository.findFirstByOrderByUpdatedAtDesc().orElse(null);
        }
        User userObj = userRepository.findById(userId)
                .or(() -> userRepository.findByEmail(userId))
                .orElse(null);

        String candidateName = (profile != null && profile.getName() != null && !profile.getName().isBlank())
                ? profile.getName()
                : (userObj != null && userObj.getName() != null && !userObj.getName().isBlank() ? userObj.getName() : "Candidate");

        String targetRole = (profile != null && profile.getTargetRole() != null && !profile.getTargetRole().isBlank())
                ? profile.getTargetRole()
                : "Software Engineer";

        String skillsList = (profile != null && profile.getSkills() != null && !profile.getSkills().isEmpty())
                ? String.join(", ", profile.getSkills())
                : "Java, Spring Boot, React, SQL, REST APIs";

        String projectsList = (profile != null && profile.getProjects() != null && !profile.getProjects().isEmpty())
                ? String.join(", ", profile.getProjects())
                : (profile != null && profile.getResumeProjects() != null && !profile.getResumeProjects().isEmpty()
                    ? String.join(", ", profile.getResumeProjects())
                    : "Enterprise Web Applications");

        String aiPrompt = String.format(
            "You are an elite technical career advisor, hiring manager, and principal software engineer.\n" +
            "The candidate has requested a custom document named '%s' for their career workspace.\n\n" +
            "Candidate Profile Context:\n" +
            "- Candidate Name: %s\n" +
            "- Target Role: %s\n" +
            "- Core Skills: %s\n" +
            "- Demonstrated Projects: %s\n\n" +
            "User's Specific Requirement / Prompt:\n%s\n\n" +
            "CRITICAL FORMATTING INSTRUCTIONS:\n" +
            "- Write a complete, comprehensive, beautifully structured Markdown document.\n" +
            "- NEVER use placeholder tokens like [Name], [Candidate Name], [Company], [Date], [Insert Here], or [City, State]. Use the candidate's actual details provided above.\n" +
            "- Provide clear explanations, production-ready code examples, and actionable technical takeaways.\n",
            rawName,
            candidateName,
            targetRole,
            skillsList,
            projectsList,
            prompt.trim()
        );

        String content;
        try {
            content = aiService.generateContent(aiPrompt);
            content = cleanPlaceholders(content, candidateName, targetRole);
        } catch (Exception e) {
            log.warn("AI generation failed for custom file {}: {}", rawName, e.getMessage());
            content = "# " + rawName.replace(".md", "") + "\n\n" +
                      "**Candidate**: " + candidateName + "\n" +
                      "**Target Role**: " + targetRole + "\n" +
                      "**Generated at**: " + LocalDateTime.now().format(DATE_FORMATTER) + "\n\n" +
                      "---\n\n" +
                      "## Overview\n\n" + prompt.trim() + "\n\n" +
                      "Document compiled successfully for your preparation.\n";
        }

        Path userRoot = userWorkspacePath(userId);
        Path targetDir = userRoot.resolve(safeFolder);
        try {
            Files.createDirectories(targetDir);
            Path filePath = targetDir.resolve(rawName).normalize();
            if (!filePath.startsWith(userRoot)) {
                throw new IllegalArgumentException("Invalid file destination.");
            }
            writeFileSafely(filePath, content);

            String relativePath = safeFolder + "/" + rawName;
            Map<String, Object> result = new HashMap<>();
            result.put("filePath", relativePath);
            result.put("fileName", rawName);
            result.put("folder", safeFolder);
            result.put("content", content);
            return result;
        } catch (Exception e) {
            log.error("Failed to save custom file {} for user {}: {}", rawName, userId, e.getMessage());
            throw new IllegalStateException("Failed to save custom file: " + e.getMessage());
        }
    }

    public void deleteFile(String userId, String relativePath) {
        Path userRoot = userWorkspacePath(userId);
        Path requested = userRoot.resolve(relativePath == null ? "" : relativePath).normalize();
        if (!requested.startsWith(userRoot) || !Files.isRegularFile(requested)) {
            throw new IllegalArgumentException("Workspace file is not available.");
        }
        try {
            Files.deleteIfExists(requested);
            log.info("Deleted workspace file {} for user {}", relativePath, userId);

            // Clean up empty parent directory if not userRoot
            Path parent = requested.getParent();
            if (parent != null && !parent.equals(userRoot)) {
                try (var stream = Files.list(parent)) {
                    if (stream.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete workspace file: " + e.getMessage());
        }
    }

    public void clearWorkspace(String userId) {
        Path userRoot = userWorkspacePath(userId);
        if (!Files.exists(userRoot)) return;
        try (var walk = Files.walk(userRoot)) {
            walk.sorted(Comparator.reverseOrder())
                .filter(p -> !p.equals(userRoot))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            log.info("Cleared all workspace files for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to clear workspace for user {}: {}", userId, e.getMessage());
            throw new IllegalStateException("Failed to clear workspace: " + e.getMessage());
        }
    }

    private void writeFileSafely(Path targetPath, String content) throws Exception {
        Files.writeString(targetPath, content);
    }

    public List<String> listFiles(String userId) {
        try (var paths = Files.walk(userWorkspacePath(userId))) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> userWorkspacePath(userId).relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public String readFile(String userId, String relativePath) {
        Path userRoot = userWorkspacePath(userId);
        Path requested = userRoot.resolve(relativePath == null ? "" : relativePath).normalize();
        if (!requested.startsWith(userRoot) || !Files.isRegularFile(requested)) {
            throw new IllegalArgumentException("Workspace file is not available.");
        }
        try {
            return Files.readString(requested);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read workspace file.");
        }
    }

    private String cleanPlaceholders(String text, String candidateName, String targetRole) {
        if (text == null) return "";
        String cleaned = text;
        if (candidateName != null && !candidateName.isBlank()) {
            cleaned = cleaned.replace("[Candidate Name]", candidateName)
                             .replace("[Candidate]", candidateName)
                             .replace("[Your Name]", candidateName);
        }
        if (targetRole != null && !targetRole.isBlank()) {
            cleaned = cleaned.replace("[Target Role]", targetRole)
                             .replace("[Target Position]", targetRole);
        }
        // Remove dummy contact lines like: [City, State/Country] | [Email Address] | [LinkedIn Profile] ...
        cleaned = cleaned.replaceAll("(?im)^.*?\\[City.*?\\][^\n]*\n?", "");
        cleaned = cleaned.replaceAll("(?im)^.*?\\[Email Address\\][^\n]*\n?", "");
        cleaned = cleaned.replaceAll("(?im)^.*?\\[LinkedIn Profile\\][^\n]*\n?", "");
        cleaned = cleaned.replaceAll("(?im)^#+\\s*\\[Candidate Name\\][^\n]*\n?", "");
        return cleaned.trim();
    }

    private Path userWorkspacePath(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }
        String safeUserId = userId.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path base = Paths.get(baseWorkspaceDir).toAbsolutePath().normalize();
        Path userRoot = base.resolve(safeUserId).normalize();
        if (!userRoot.startsWith(base)) {
            throw new IllegalArgumentException("Invalid workspace owner.");
        }
        return userRoot;
    }
}
