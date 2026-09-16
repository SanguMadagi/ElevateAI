package com.ai.interview.controller;

import com.ai.interview.service.CareerWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/workspace")
@RequiredArgsConstructor
public class CareerWorkspaceController {

    private final CareerWorkspaceService workspaceService;

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(
            Authentication authentication) {
        return ResponseEntity.ok(workspaceService.generateWorkspace(authentication.getName()));
    }

    @PostMapping("/custom-file")
    public ResponseEntity<Map<String, Object>> generateCustomFile(
            Authentication authentication,
            @RequestBody Map<String, String> payload) {
        String fileName = payload != null ? payload.get("fileName") : null;
        String folder = payload != null ? payload.get("folder") : "Custom";
        String prompt = payload != null ? payload.get("prompt") : null;
        return ResponseEntity.ok(workspaceService.generateCustomFile(authentication.getName(), fileName, folder, prompt));
    }

    @GetMapping("/files")
    public ResponseEntity<List<String>> listFiles(Authentication authentication) {
        return ResponseEntity.ok(workspaceService.listFiles(authentication.getName()));
    }

    @GetMapping("/file")
    public ResponseEntity<String> getFileByParam(
            Authentication authentication, @RequestParam("path") String path) {
        return ResponseEntity.ok(workspaceService.readFile(authentication.getName(), path));
    }

    @GetMapping("/files/{*path}")
    public ResponseEntity<String> readFile(
            Authentication authentication, @PathVariable String path) {
        return ResponseEntity.ok(workspaceService.readFile(authentication.getName(), path));
    }

    @DeleteMapping("/file")
    public ResponseEntity<Map<String, String>> deleteFile(
            Authentication authentication,
            @RequestParam("path") String path) {
        workspaceService.deleteFile(authentication.getName(), path);
        return ResponseEntity.ok(Map.of("message", "File deleted successfully", "path", path));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, String>> clearWorkspace(
            Authentication authentication) {
        workspaceService.clearWorkspace(authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Workspace cleared successfully"));
    }
}
