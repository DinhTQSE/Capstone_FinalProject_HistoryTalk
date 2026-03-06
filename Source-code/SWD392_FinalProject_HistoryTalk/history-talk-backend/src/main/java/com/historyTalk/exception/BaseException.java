package com.historyTalk.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class BaseException extends RuntimeException {
    private final int errorCode;
    private final HttpStatus httpStatus;

    public BaseException(String message,int errorCode,HttpStatus httpStatus ){
        super(message);
        this.errorCode=errorCode;
        this.httpStatus=httpStatus;
    }

}
