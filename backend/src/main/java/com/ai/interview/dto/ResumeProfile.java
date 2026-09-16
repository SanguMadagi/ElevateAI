package com.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeProfile {
    private String name = "";
    private String firstName = "";
    private String lastName = "";
    private String email = "";
    private String phone = "";
    private String currentCompany = "";
    private String suggestedRole = "";
    private List<String> skills = new ArrayList<>();
    private List<String> technologies = new ArrayList<>();
    private List<String> education = new ArrayList<>();
    private List<String> experience = new ArrayList<>();
    private List<String> projects = new ArrayList<>();
    private List<String> achievements = new ArrayList<>();
    private List<String> certifications = new ArrayList<>();
    private List<String> claims = new ArrayList<>();
}
