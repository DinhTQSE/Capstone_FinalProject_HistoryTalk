package com.historytalk.service.notification;

public interface GoogleOAuthPasswordEmailService {

    void sendTemporaryPasswordEmail(String email, String userName, String temporaryPassword);
}
