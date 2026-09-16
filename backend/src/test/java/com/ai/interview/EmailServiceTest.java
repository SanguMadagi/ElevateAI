package com.ai.interview;

import com.ai.interview.service.EmailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
    }

    @Test
    void testSendOtpEmail_WhenConfigured_SendsMimeMessageWithCorrectHeaders() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailUsername", "testuser@gmail.com");
        ReflectionTestUtils.setField(emailService, "mailPassword", "test-password");
        ReflectionTestUtils.setField(emailService, "mailFrom", "ElevateAI <testuser@gmail.com>");

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        emailService.sendOtpEmail("candidate@example.com", "849201", "Your AI Interview OTP");

        // Assert
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sentMessage = captor.getValue();
        assertNotNull(sentMessage);
        assertEquals("Your AI Interview OTP", sentMessage.getSubject());
        assertNotNull(sentMessage.getAllRecipients());
        assertEquals("candidate@example.com", sentMessage.getAllRecipients()[0].toString());
    }

    @Test
    void testSendOtpEmail_WhenUnconfigured_ReportsConfigurationError() {
        // Arrange (no username configured)
        ReflectionTestUtils.setField(emailService, "mailUsername", "");

        // Act and Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> emailService.sendOtpEmail("candidate@example.com", "123456", "Your AI Interview OTP"));

        assertEquals("SMTP is not configured. Set MAIL_USERNAME and MAIL_PASSWORD before requesting an OTP.",
            exception.getMessage());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
