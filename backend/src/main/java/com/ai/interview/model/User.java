package com.ai.interview.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    private String id;
    private String name;
    private String firstName;
    private String lastName;
    private String email;
    @JsonIgnore
    private String password;
    private String role; // "CANDIDATE" or "ADMIN"
    private boolean emailVerified = false;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
