package com.ai.interview.controller;

import com.ai.interview.model.SkillAnalysis;
import com.ai.interview.service.SkillAnalyzerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillAnalyzerController {

    private final SkillAnalyzerService skillAnalyzerService;

    @GetMapping("/me")
    public ResponseEntity<SkillAnalysis> getMyAnalysis(Authentication authentication) {
        return ResponseEntity.ok(skillAnalyzerService.getAnalysis(authentication.getName()));
    }

    @PostMapping("/reanalyze")
    public ResponseEntity<SkillAnalysis> reanalyze(Authentication authentication) {
        return ResponseEntity.ok(skillAnalyzerService.analyzeUserPerformance(authentication.getName()));
    }
}