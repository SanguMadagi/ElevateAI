package com.ai.interview.controller;

import com.ai.interview.model.Profile;
import com.ai.interview.model.User;
import com.ai.interview.repository.UserRepository;
import com.ai.interview.service.ProfileService;
import com.ai.interview.exception.ResourceNotFoundException;
import com.ai.interview.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping({"/api/profile", "/api/v1/profiles"})
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final UserRepository userRepository;

    @PostMapping({"/create", ""})
    public ResponseEntity<Profile> createProfile(@RequestBody Map<String, Object> body, Authentication authentication) {
        String userId = authentication.getName();
        User userObj = userRepository.findById(userId).orElse(null);
        
        Profile profile = new Profile();
        
        String firstName = body.containsKey("firstName") ? (String) body.get("firstName") : null;
        String lastName = body.containsKey("lastName") ? (String) body.get("lastName") : null;
        if (firstName == null && userObj != null) {
            firstName = userObj.getFirstName();
        }
        if (lastName == null && userObj != null) {
            lastName = userObj.getLastName();
        }
        profile.setFirstName(firstName);
        profile.setLastName(lastName);

        String name = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        if (name.isEmpty()) {
            name = (userObj != null) ? userObj.getName() : "User";
        }
        profile.setName(name);
        
        String email = body.containsKey("email") ? (String) body.get("email") : null;
        if (email == null || email.isBlank()) {
            email = (userObj != null) ? userObj.getEmail() : "";
        }
        profile.setEmail(email);
        
        profile.setPhone((String) body.get("phone"));
        
        String suggestedRole = body.containsKey("suggestedRole") ? (String) body.get("suggestedRole") : null;
        profile.setSuggestedRole(suggestedRole);

        String targetRole = body.containsKey("targetRole") ? (String) body.get("targetRole") : suggestedRole;
        if (targetRole == null && body.containsKey("role")) {
            targetRole = (String) body.get("role");
        }
        profile.setTargetRole(targetRole);
        
        String expertiseLevel = null;
        if (body.containsKey("expertiseLevel")) {
            expertiseLevel = (String) body.get("expertiseLevel");
        } else if (body.containsKey("level")) {
            expertiseLevel = (String) body.get("level");
        }
        profile.setExpertiseLevel(expertiseLevel);
        
        if (body.containsKey("skills")) {
            profile.setSkills((List<String>) body.get("skills"));
        }
        
        if (body.containsKey("education")) {
            profile.setEducation((List<String>) body.get("education"));
        }
        if (body.containsKey("experience")) {
            if (body.get("experience") instanceof List) {
                profile.setExperience((List<String>) body.get("experience"));
            }
        }
        if (body.containsKey("projects")) {
            profile.setProjects((List<String>) body.get("projects"));
            profile.setResumeProjects((List<String>) body.get("projects"));
        }
        if (body.containsKey("certifications")) {
            profile.setCertifications((List<String>) body.get("certifications"));
        }
        
        if (body.containsKey("resumeTechnologies")) {
            profile.setResumeTechnologies((List<String>) body.get("resumeTechnologies"));
        } else if (body.containsKey("technologies")) {
            profile.setResumeTechnologies((List<String>) body.get("technologies"));
        } else if (profile.getSkills() != null) {
            profile.setResumeTechnologies(profile.getSkills());
        }
        
        profile.setEducationLevel((String) body.get("educationLevel"));
        if (body.containsKey("yearsOfExperience")) {
            Object expObj = body.get("yearsOfExperience");
            if (expObj instanceof Number) {
                profile.setYearsOfExperience(((Number) expObj).intValue());
            }
        }
        profile.setCurrentCompany((String) body.get("currentCompany"));

        // Keep User identity in sync
        if (userObj != null) {
            boolean userChanged = false;
            if (firstName != null && !firstName.equals(userObj.getFirstName())) {
                userObj.setFirstName(firstName);
                userChanged = true;
            }
            if (lastName != null && !lastName.equals(userObj.getLastName())) {
                userObj.setLastName(lastName);
                userChanged = true;
            }
            if (!name.equals(userObj.getName())) {
                userObj.setName(name);
                userChanged = true;
            }
            if (userChanged) {
                userRepository.save(userObj);
            }
        }

        Profile saved = profileService.saveOrCreateProfile(userId, profile);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/upload-resume")
    public ResponseEntity<Profile> uploadResume(@RequestParam("file") MultipartFile file, Authentication authentication) {
        if (file.isEmpty() || (file.getContentType() != null && !file.getContentType().equalsIgnoreCase("application/pdf"))) {
            throw new BadRequestException("Only PDF files are allowed");
        }
        String userId = authentication.getName();
        Profile updated = profileService.handleResumeUpload(userId, file);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/me")
    public ResponseEntity<Profile> getMyProfile(Authentication authentication) {
        String userId = authentication.getName();
        Profile profile = profileService.getProfileByUserId(userId);
        if (profile == null) {
            User userObj = userRepository.findById(userId).orElse(null);
            if (userObj == null) {
                throw new ResourceNotFoundException("User not found");
            }
            profile = new Profile();
            profile.setUserId(userId);
            profile.setName(userObj.getName());
            profile.setFirstName(userObj.getFirstName());
            profile.setLastName(userObj.getLastName());
            profile.setEmail(userObj.getEmail());
            profile.setSkills(java.util.Collections.emptyList());
            profile.setEducation(java.util.Collections.emptyList());
            profile.setExperience(java.util.Collections.emptyList());
            profile.setProjects(java.util.Collections.emptyList());
            profile.setCertifications(java.util.Collections.emptyList());
        }
        return ResponseEntity.ok(profile);
    }
}
