package com.historytalk.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BaseException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED);
    }
    
    public UnauthorizedException(String message, Throwable cause) {
        super(message, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED, cause);
    }
}
