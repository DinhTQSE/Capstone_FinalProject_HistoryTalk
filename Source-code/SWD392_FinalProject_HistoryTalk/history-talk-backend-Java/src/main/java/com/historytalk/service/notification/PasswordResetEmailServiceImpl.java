package com.historytalk.service.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PasswordResetEmailServiceImpl implements PasswordResetEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.password-reset.frontend-url}")
    private String frontendResetUrl;

    @Value("${app.password-reset.token-expiration-minutes}")
    private long tokenExpirationMinutes;

    @Override
    public void sendPasswordResetEmail(String email, String userName, String resetToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject("Reset your HistoryTalk password");
            helper.setText(buildEmailBody(userName, buildResetLink(resetToken)));

            mailSender.send(message);
        } catch (MessagingException | RuntimeException ex) {
            throw new IllegalStateException("Không thể gửi email đặt lại mật khẩu", ex);
        }
    }

    private String buildResetLink(String resetToken) {
        return UriComponentsBuilder.fromUriString(frontendResetUrl)
                .queryParam("token", resetToken)
                .build()
                .toUriString();
    }

    private String buildEmailBody(String userName, String resetLink) {
        return """
                Hello %s,

                We received a request to reset your HistoryTalk password.

                Open this link to set a new password:
                %s

                This link expires in %d minutes.

                If you did not request a password reset, ignore this email.
                """.formatted(userName, resetLink, tokenExpirationMinutes);
    }
}
