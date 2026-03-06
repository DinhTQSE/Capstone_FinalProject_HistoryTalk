package com.historyTalk.exception;

import lombok.Builder;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
@Builder
public class ErrorResponse {
    private final int errorCode;
    private final String message;
    private final HttpStatus status;
    private final LocalDateTime timestamp;
    public ErrorResponse(int errorCode, String message, HttpStatus status, LocalDateTime timestamp){
        this.errorCode=errorCode;
        this.message=message;
        this.status=status;
        this.timestamp=timestamp;
    }
}
