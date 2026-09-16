package com.ai.interview;

import com.ai.interview.service.CareerWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class CareerWorkspaceSecurityTest {

    @Autowired
    private CareerWorkspaceService workspaceService;

    @Test
    void testPathTraversalRejected() {
        String user = "testuser123";

        // Path traversal attempts
        assertThrows(IllegalArgumentException.class, () -> {
            workspaceService.readFile(user, "../../../etc/passwd");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            workspaceService.readFile(user, "..\\..\\windows\\system32\\drivers\\etc\\hosts");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            workspaceService.readFile(user, "/etc/shadow");
        });
    }
}
