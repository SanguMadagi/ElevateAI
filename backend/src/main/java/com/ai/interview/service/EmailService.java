package com.ai.interview.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @PostConstruct
    public void init() {
        if (mailSender instanceof JavaMailSenderImpl impl) {
            String user = impl.getUsername();
            String pass = impl.getPassword();
            boolean passConfigured = (pass != null && !pass.trim().isEmpty());
            int passLength = pass != null ? pass.length() : 0;
            var properties = impl.getJavaMailProperties();
            String resolvedFrom = resolveFromAddress();
            log.info("SMTP runtime configuration -> host={}, port={}, usernameLoaded={}, passwordLoaded={}, passwordLength={}, " +
                    "auth={}, starttlsEnabled={}, starttlsRequired={}, fromAddress={}, usernameMatchesFrom={}, " +
                    "passwordHasOuterWhitespace={}",
                impl.getHost(), impl.getPort(), hasText(user), passConfigured, passLength,
                properties.getProperty("mail.smtp.auth"),
                properties.getProperty("mail.smtp.starttls.enable"),
                properties.getProperty("mail.smtp.starttls.required"),
                resolvedFrom, fromMatchesUsername(user), hasOuterWhitespace(pass));
            if (!passConfigured && user != null && !user.trim().isEmpty()) {
                log.warn("[SMTP CONFIG WARNING] MAIL_USERNAME is loaded but MAIL_PASSWORD is empty or not loaded from the backend process environment.");
            }
        }
    }

    public void sendOtpEmail(String email, String otp, String subject) {
        String maskedEmail = maskEmail(email);
        log.info("Initiating OTP email delivery to: {} with subject: '{}'", maskedEmail, subject);

        // Check if SMTP credentials are configured
        if (!hasText(mailUsername) || !hasText(mailPassword)) {
          throw new IllegalStateException("SMTP is not configured. Set MAIL_USERNAME and MAIL_PASSWORD before requesting an OTP.");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(resolveFromAddress(), "ElevateAI Interview Platform");
            helper.setTo(email);
            helper.setSubject(subject != null ? subject : "Your Verification Code - ElevateAI");

            String plainText = String.format(
                "Hello,\n\nYour verification code for ElevateAI Interview Platform is: %s\n\n" +
                "This code is valid for 10 minutes.\n\n" +
                "If you did not request this verification code, please ignore this email.\n\n" +
                "Best regards,\nElevateAI Team",
                otp
            );

            String htmlText = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Verification Code</title>
                </head>
                <body style="margin:0;padding:0;background-color:#0f172a;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#f8fafc;">
                  <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width:560px;margin:40px auto;background-color:#1e293b;border-radius:16px;border:1px solid #334155;overflow:hidden;box-shadow:0 10px 25px rgba(0,0,0,0.3);">
                    <tr>
                      <td style="padding:32px 32px 24px;text-align:center;background:linear-gradient(135deg,#7c3aed 0%%,#4f46e5 100%%);">
                        <h1 style="margin:0;font-size:24px;font-weight:900;color:#ffffff;letter-spacing:-0.5px;">Elevate<span style="color:#c4b5fd;">AI</span></h1>
                        <p style="margin:4px 0 0;font-size:12px;color:#e2e8f0;text-transform:uppercase;letter-spacing:1.5px;font-weight:700;">AI Interview & Career Preparation</p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:32px 32px 24px;">
                        <h2 style="margin:0 0 12px;font-size:18px;font-weight:700;color:#f8fafc;">Verify Your Identity</h2>
                        <p style="margin:0 0 24px;font-size:14px;line-height:1.6;color:#94a3b8;">
                          Use the verification code below to complete your authentication. This one-time password (OTP) is valid for <strong>10 minutes</strong>.
                        </p>
                        <div style="text-align:center;margin:28px 0;padding:18px;background-color:#0f172a;border-radius:12px;border:1px solid #475569;">
                          <span style="font-family:'Courier New',Courier,monospace;font-size:36px;font-weight:900;letter-spacing:8px;color:#a78bfa;">%s</span>
                        </div>
                        <p style="margin:0 0 16px;font-size:13px;line-height:1.5;color:#64748b;">
                          If you did not initiate this request, please safely disregard this message. Do not share this code with anyone.
                        </p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:20px 32px;background-color:#0f172a;border-top:1px solid #334155;text-align:center;">
                        <p style="margin:0;font-size:11px;color:#475569;">
                          &copy; 2026 ElevateAI Platform. Automated verification notification.
                        </p>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """, otp);

            helper.setText(plainText, htmlText);

            mailSender.send(mimeMessage);
            log.info("OTP email successfully sent to SMTP server for recipient: {}", maskedEmail);

        } catch (MailAuthenticationException e) {
          log.error("[SMTP AUTH ERROR] Authentication failed for username {}. SMTP response: {}",
              maskEmail(mailUsername), smtpResponse(e), e);
          throw new IllegalStateException("SMTP authentication failed. Check MAIL_USERNAME and use a Gmail App Password.", e);
        } catch (MailSendException e) {
          log.error("[SMTP SEND ERROR] Failed to deliver email to {}. SMTP response: {}", maskedEmail, smtpResponse(e), e);
          throw new IllegalStateException("SMTP could not deliver the OTP email. Check the mail server settings.", e);
        } catch (Exception e) {
            log.error("[EMAIL ERROR] Unexpected failure sending OTP email to {}: {}", maskedEmail, e.getMessage(), e);
                  throw new IllegalStateException("Unable to send the OTP email. Check the mail server settings.", e);
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }

      private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
      }

      private boolean hasOuterWhitespace(String value) {
        return value != null && !value.equals(value.trim());
      }

      private boolean fromMatchesUsername(String username) {
        String from = resolveFromAddress();
        return hasText(username) && hasText(from) &&
            (from.equalsIgnoreCase(username) || from.toLowerCase().endsWith("<" + username.toLowerCase() + ">"));
      }

      private String resolveFromAddress() {
        return (mailFrom != null && !mailFrom.trim().isEmpty()) ? mailFrom.trim() : mailUsername.trim();
      }

      private String smtpResponse(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
          if (current instanceof MessagingException messagingException && messagingException.getNextException() != null) {
            return messagingException.getNextException().getMessage();
          }
          current = current.getCause();
        }
        return failure.getMessage();
      }
}
