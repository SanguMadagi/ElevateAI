package com.ai.interview.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Violation {
    private String type; // TAB_SWITCH / COPY_PASTE / FULLSCREEN_EXIT / DEVTOOLS / MULTIPLE_TABS / RIGHT_CLICK
    private String severity; // HIGH / MEDIUM / LOW
    private LocalDateTime timestamp;
    private String description;
}
