package com.ai.interview.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Profile {
    @Id
    private String id;
    private String userId;
    private String name;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String targetRole;
    private String suggestedRole;
    private String expertiseLevel; // BEGINNER / INTERMEDIATE / SENIOR / EXPERT
    private List<String> skills;
    private String educationLevel;
    private List<String> education;
    private int yearsOfExperience;
    private String currentCompany;
    private String resumeText;
    private String resumeHash;
    private String resumeFileName;
    private List<String> resumeProjects;
    private List<String> resumeTechnologies;
    private List<String> experience;
    private List<String> projects;
    private List<String> certifications;
    private boolean aiAnalysisFailed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
