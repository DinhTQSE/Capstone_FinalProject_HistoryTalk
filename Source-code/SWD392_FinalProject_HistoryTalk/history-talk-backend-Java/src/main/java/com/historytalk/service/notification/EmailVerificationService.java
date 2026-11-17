package com.historytalk.service.notification;

public interface EmailVerificationService {

    void sendVerificationEmail(String email, String userName, String verificationToken);
}
