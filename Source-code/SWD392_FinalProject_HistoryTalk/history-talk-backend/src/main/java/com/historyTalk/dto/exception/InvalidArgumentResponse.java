package com.historyTalk.dto.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
public class InvalidArgumentResponse {
    private int errorCode;
    private Map<String, String> errors; // key = field, value = message
    private HttpStatus status;
    private LocalDateTime timestamp;
}
