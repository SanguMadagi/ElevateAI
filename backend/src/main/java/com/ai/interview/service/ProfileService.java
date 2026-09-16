package com.ai.interview.service;

import com.ai.interview.model.Profile;
import com.ai.interview.repository.ProfileRepository;
import com.ai.interview.utils.JsonUtils;
import com.ai.interview.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Profile getProfileByUserId(String userId) {
        return profileRepository.findByUserId(userId).orElse(null);
    }

    public Profile saveOrCreateProfile(String userId, Profile profile) {
        Optional<Profile> existingOpt = profileRepository.findByUserId(userId);
        Profile toSave;
        if (existingOpt.isPresent()) {
            toSave = existingOpt.get();
            toSave.setName(profile.getName());
            toSave.setFirstName(profile.getFirstName());
            toSave.setLastName(profile.getLastName());
            toSave.setEmail(profile.getEmail());
            toSave.setPhone(profile.getPhone());
            toSave.setTargetRole(profile.getTargetRole());
            toSave.setSuggestedRole(profile.getSuggestedRole());
            toSave.setExpertiseLevel(profile.getExpertiseLevel());
            toSave.setSkills(uniqueValues(profile.getSkills()));
            toSave.setEducationLevel(profile.getEducationLevel());
            toSave.setEducation(uniqueValues(profile.getEducation()));
            toSave.setYearsOfExperience(profile.getYearsOfExperience());
            toSave.setCurrentCompany(profile.getCurrentCompany());
            toSave.setResumeProjects(uniqueValues(profile.getResumeProjects()));
            toSave.setResumeTechnologies(uniqueValues(profile.getResumeTechnologies()));
            toSave.setExperience(uniqueValues(profile.getExperience()));
            toSave.setProjects(uniqueValues(profile.getProjects()));
            toSave.setCertifications(uniqueValues(profile.getCertifications()));
            toSave.setUpdatedAt(LocalDateTime.now());
        } else {
            toSave = profile;
            toSave.setUserId(userId);
            toSave.setSkills(uniqueValues(profile.getSkills()));
            toSave.setEducation(uniqueValues(profile.getEducation()));
            toSave.setExperience(uniqueValues(profile.getExperience()));
            toSave.setProjects(uniqueValues(profile.getProjects()));
            toSave.setCertifications(uniqueValues(profile.getCertifications()));
            toSave.setCreatedAt(LocalDateTime.now());
            toSave.setUpdatedAt(LocalDateTime.now());
        }
        return profileRepository.save(toSave);
    }

    public String extractTextFromPdf(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            log.error("Error extracting text from PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to read PDF file", e);
        }
    }

    public Profile handleResumeUpload(String userId, MultipartFile file) {
        String resumeText = extractTextFromPdf(file);
        if (resumeText == null || resumeText.isBlank()) {
            throw new BadRequestException("The PDF does not contain readable resume text. Please upload another PDF.");
        }
        String fileName = file.getOriginalFilename();

        Profile profile = getProfileByUserId(userId);
        if (profile == null) {
            profile = new Profile();
            profile.setUserId(userId);
            profile.setCreatedAt(LocalDateTime.now());
        }

        String resumeHash = calculateResumeHash(resumeText);
        profile.setResumeText(resumeText);
        profile.setResumeHash(resumeHash);
        profile.setResumeFileName(fileName);
        profile.setUpdatedAt(LocalDateTime.now());
        profile.setAiAnalysisFailed(false); // Reset failure flag

        boolean alreadyProcessed = profile.getResumeHash() != null
                && profile.getResumeHash().equals(resumeHash)
                && profile.getSkills() != null
                && !profile.getSkills().isEmpty()
                && profile.getTargetRole() != null
                && !profile.getTargetRole().isBlank();
        if (alreadyProcessed) {
            log.info("Resume hash already processed for user {}. Skipping redundant Gemini parse.", userId);
            return profileRepository.save(profile);
        }

        String prompt = "You are an expert resume parsing AI. Analyze the following resume text and extract the details as a valid JSON object matching the schema below. Respond with ONLY the raw JSON object, no markdown code blocks or explanations.\n" +
                "{\n" +
                "  \"firstName\": \"string (extract first name if present, else empty)\",\n" +
                "  \"lastName\": \"string (extract last name if present, else empty)\",\n" +
                "  \"email\": \"string (extract email if present, else empty)\",\n" +
                "  \"phone\": \"string (extract phone/mobile number if present, else empty; use this exact key)\",\n" +
                "  \"currentCompany\": \"string (extract current/most recent company if present, else empty)\",\n" +
                "  \"skills\": [\"list\", \"of\", \"technical\", \"skills\"],\n" +
                "  \"education\": [\"list\", \"of\", \"degrees/schools/institutions\"],\n" +
                "  \"experience\": [\"list\", \"of\", \"roles/companies/work experience items\"],\n" +
                "  \"projects\": [\"list\", \"of\", \"projects/descriptions\"],\n" +
                "  \"certifications\": [\"list\", \"of\", \"certifications\"],\n" +
                "  \"suggestedRole\": \"string (a suggested target role matching their experience e.g. Java Backend Developer)\"\n" +
                "}\n" +
                "Resume text: " + resumeText;

        try {
            String response = aiService.generateContent(prompt);

            // Validate response before parsing
            if (response == null || response.trim().isEmpty()) {
                throw new RuntimeException("Gemini API returned an empty response.");
            }
            
            String cleaned = JsonUtils.cleanJsonResponse(response);
            Map<String, Object> map = objectMapper.readValue(cleaned, Map.class);

            if (map != null) {
                setIfNotBlank(map, "firstName", profile::setFirstName);
                setIfNotBlank(map, "lastName", profile::setLastName);
                setIfNotBlank(map, "email", profile::setEmail);
                setIfNotBlank(map, "phone", profile::setPhone);
                if ((profile.getPhone() == null || profile.getPhone().isBlank())) {
                    setIfNotBlank(map, "phoneNumber", profile::setPhone);
                    setIfNotBlank(map, "mobile", profile::setPhone);
                }
                setIfNotBlank(map, "currentCompany", profile::setCurrentCompany);
                
                if (hasValues(map.get("skills"))) {
                    profile.setSkills(toTextList(map.get("skills")));
                    profile.setResumeTechnologies(toTextList(map.get("skills")));
                }
                if (hasValues(map.get("education"))) profile.setEducation(toTextList(map.get("education")));
                if (hasValues(map.get("experience"))) profile.setExperience(toTextList(map.get("experience")));
                
                if (hasValues(map.get("projects"))) {
                    profile.setProjects(toTextList(map.get("projects")));
                    profile.setResumeProjects(toTextList(map.get("projects")));
                }
                if (hasValues(map.get("certifications"))) profile.setCertifications(toTextList(map.get("certifications")));
                if (map.get("suggestedRole") instanceof String suggestedRole && !suggestedRole.isBlank()) {
                    profile.setSuggestedRole(suggestedRole);
                    if (profile.getTargetRole() == null || profile.getTargetRole().isBlank()) {
                        profile.setTargetRole(suggestedRole);
                    }
                }

                log.info("Resume extraction fields: phonePresent={}, educationCount={}, certificationsCount={}, experienceCount={}, projectsCount={}, skillsCount={}",
                    profile.getPhone() != null && !profile.getPhone().isBlank(),
                    profile.getEducation() == null ? 0 : profile.getEducation().size(),
                    profile.getCertifications() == null ? 0 : profile.getCertifications().size(),
                    profile.getExperience() == null ? 0 : profile.getExperience().size(),
                    profile.getProjects() == null ? 0 : profile.getProjects().size(),
                    profile.getSkills() == null ? 0 : profile.getSkills().size());

                String combinedName = ((profile.getFirstName() != null ? profile.getFirstName() : "") + " " +
                                      (profile.getLastName() != null ? profile.getLastName() : "")).trim();
                if (!combinedName.isEmpty()) {
                    profile.setName(combinedName);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Gemini response for resume: {}", e.getMessage());
            profile.setAiAnalysisFailed(true);
            profileRepository.save(profile);
            throw new BadRequestException("We could not parse this resume. Please check the PDF and try again.");
        }

        return profileRepository.save(profile);
    }

    private String calculateResumeHash(String resumeText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(resumeText.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable.", e);
        }
    }

    private void setIfNotBlank(Map<String, Object> values, String key, java.util.function.Consumer<String> setter) {
        Object value = values.get(key);
        if (value instanceof String text && !text.isBlank()) {
            setter.accept(text.trim());
        }
    }

    private List<String> uniqueValues(List<String> values) {
        if (values == null) return null;
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private boolean hasValues(Object value) {
        return value instanceof List<?> list && !list.isEmpty();
    }

    private List<String> toTextList(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(item -> {
                    if (item instanceof String text) return text;
                    if (item instanceof Map<?, ?> details) {
                        return details.entrySet().stream()
                                .filter(entry -> entry.getValue() != null)
                                .map(entry -> entry.getKey() + ": " + entry.getValue())
                                .collect(java.util.stream.Collectors.joining(" | "));
                    }
                    return String.valueOf(item);
                })
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .distinct()
                .toList();
    }
}
