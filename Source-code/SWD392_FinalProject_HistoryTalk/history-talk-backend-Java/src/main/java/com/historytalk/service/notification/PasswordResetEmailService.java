package com.historytalk.service.notification;

public interface PasswordResetEmailService {

    void sendPasswordResetEmail(String email, String userName, String resetToken);
}
