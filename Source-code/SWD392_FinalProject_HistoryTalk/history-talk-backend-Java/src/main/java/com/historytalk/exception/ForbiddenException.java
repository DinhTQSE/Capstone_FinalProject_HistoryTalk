package com.historytalk.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated caller attempts an action they are not permitted to perform.
 * Maps to HTTP 403 Forbidden.
 */
public class ForbiddenException extends BaseException {

    private static final int ERROR_CODE = HttpStatus.FORBIDDEN.value();

    public ForbiddenException(String message) {
        super(message, ERROR_CODE, HttpStatus.FORBIDDEN);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, ERROR_CODE, HttpStatus.FORBIDDEN, cause);
    }
}
