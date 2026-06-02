package com.historytalk.service.notification;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthPasswordEmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendTemporaryPasswordEmailSendsExpectedMessageContent() throws Exception {
        GoogleOAuthPasswordEmailServiceImpl service = new GoogleOAuthPasswordEmailServiceImpl(mailSender);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@historytalk.com");
        MimeMessage mimeMessage = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendTemporaryPasswordEmail("user@gmail.com", "New User", "HT-GOOGLE-abc123");

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("Your HistoryTalk temporary password", sentMessage.getSubject());
        assertTrue(containsAddress(sentMessage.getRecipients(Message.RecipientType.TO), "user@gmail.com"));
        assertTrue(containsAddress(sentMessage.getFrom(), "no-reply@historytalk.com"));

        String body = (String) sentMessage.getContent();
        assertTrue(body.contains("HT-GOOGLE-abc123"));
        assertTrue(body.contains("PATCH /Historical-tell/api/v1/users/me/password"));
        assertTrue(body.contains("not your Google password"));
    }

    private boolean containsAddress(Address[] addresses, String expectedAddress) {
        return Arrays.stream(addresses)
                .map(Address::toString)
                .anyMatch(address -> address.contains(expectedAddress));
    }
}
