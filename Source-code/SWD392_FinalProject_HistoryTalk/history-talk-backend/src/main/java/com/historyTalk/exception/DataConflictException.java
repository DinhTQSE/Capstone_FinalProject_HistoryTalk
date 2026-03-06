package com.historyTalk.exception;

import org.springframework.http.HttpStatus;

public class DataConflictException extends BaseException{

    public DataConflictException(String message) {
        super(message, HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT);
    }
}
