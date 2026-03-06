package com.historyTalk.exception;

import org.springframework.http.HttpStatus;

public class SystemException extends BaseException{

    public SystemException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
