package com.historytalk.service.notification;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendVerificationEmailSendsExpectedMessageContent() throws Exception {
        EmailVerificationServiceImpl service = new EmailVerificationServiceImpl(mailSender);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@historytalk.com");
        ReflectionTestUtils.setField(service, "frontendVerificationUrl", "https://app.historytalk.com/verify-email");
        ReflectionTestUtils.setField(service, "tokenExpirationMinutes", 30L);
        MimeMessage mimeMessage = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendVerificationEmail("user@gmail.com", "History User", "verify-token-123");

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertThat(sentMessage.getSubject()).isEqualTo("Verify your HistoryTalk account");
        assertTrue(containsAddress(sentMessage.getRecipients(Message.RecipientType.TO), "user@gmail.com"));
        assertTrue(containsAddress(sentMessage.getFrom(), "no-reply@historytalk.com"));

        String body = (String) sentMessage.getContent();
        assertThat(body).contains("Hello History User");
        assertThat(body).contains("https://app.historytalk.com/verify-email?token=verify-token-123");
        assertThat(body).contains("30 minutes");
    }

    @Test
    void sendVerificationEmailWrapsMailSendFailure() {
        EmailVerificationServiceImpl service = new EmailVerificationServiceImpl(mailSender);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@historytalk.com");
        ReflectionTestUtils.setField(service, "frontendVerificationUrl", "https://app.historytalk.com/verify-email");
        ReflectionTestUtils.setField(service, "tokenExpirationMinutes", 30L);
        MimeMessage mimeMessage = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        MailSendException failure = new MailSendException("smtp failure");
        doThrow(failure).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.sendVerificationEmail("user@gmail.com", "History User", "verify-token-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Không thể gửi email xác thực tài khoản")
                .hasCause(failure);
    }

    private boolean containsAddress(Address[] addresses, String expectedAddress) {
        return Arrays.stream(addresses)
                .map(Address::toString)
                .anyMatch(address -> address.contains(expectedAddress));
    }
}
