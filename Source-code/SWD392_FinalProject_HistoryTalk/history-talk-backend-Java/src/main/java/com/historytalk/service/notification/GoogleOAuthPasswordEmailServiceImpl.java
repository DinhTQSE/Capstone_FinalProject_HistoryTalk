package com.historytalk.service.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleOAuthPasswordEmailServiceImpl implements GoogleOAuthPasswordEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Override
    public void sendTemporaryPasswordEmail(String email, String userName, String temporaryPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(email);
            helper.setSubject("Your HistoryTalk temporary password");
            helper.setText(buildEmailBody(userName, temporaryPassword));

            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new IllegalStateException("Failed to send Google OAuth temporary password email", ex);
        }
    }

    private String buildEmailBody(String userName, String temporaryPassword) {
        return """
                Hello %s,

                Your HistoryTalk account was created using Google OAuth.

                We created a temporary application password for your HistoryTalk account:
                %s

                This is not your Google password. It only works as a temporary application password for HistoryTalk.

                Please change after signing in.
                """.formatted(userName, temporaryPassword);
    }
}
