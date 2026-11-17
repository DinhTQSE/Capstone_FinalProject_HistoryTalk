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
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.email-verification.frontend-url}")
    private String frontendVerificationUrl;

    @Value("${app.email-verification.token-expiration-minutes}")
    private long tokenExpirationMinutes;

    @Override
    public void sendVerificationEmail(String email, String userName, String verificationToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject("Verify your HistoryTalk account");
            helper.setText(buildEmailBody(userName, buildVerificationLink(verificationToken)));

            mailSender.send(message);
        } catch (MessagingException | RuntimeException ex) {
            throw new IllegalStateException("Không thể gửi email xác thực tài khoản", ex);
        }
    }

    private String buildVerificationLink(String verificationToken) {
        return UriComponentsBuilder.fromUriString(frontendVerificationUrl)
                .queryParam("token", verificationToken)
                .build()
                .toUriString();
    }

    private String buildEmailBody(String userName, String verificationLink) {
        return """
                Hello %s,

                Thank you for registering a HistoryTalk account.

                Open this link to verify your email address:
                %s

                This link expires in %d minutes.

                If you did not create this account, please ignore this email.
                """.formatted(userName, verificationLink, tokenExpirationMinutes);
    }
}
