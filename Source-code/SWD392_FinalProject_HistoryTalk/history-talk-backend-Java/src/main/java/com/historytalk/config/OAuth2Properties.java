package com.historytalk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class OAuth2Properties {

    @Value("${app.oauth2.success-redirect-url}")
    private String successRedirectUrl;

    @Value("${app.oauth2.failure-redirect-url}")
    private String failureRedirectUrl;
}
