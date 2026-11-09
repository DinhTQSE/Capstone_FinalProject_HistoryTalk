package com.historytalk.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BaseException {

    public RateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS);
    }
}
