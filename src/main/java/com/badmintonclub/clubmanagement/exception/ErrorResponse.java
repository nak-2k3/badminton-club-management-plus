package com.badmintonclub.clubmanagement.exception;

import java.time.LocalDateTime;
import java.util.Map;

// Định dạng JSON lỗi thống nhất trả về cho frontend
public record ErrorResponse(
        int status,
        String message,
        Map<String, String> errors,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, null, LocalDateTime.now());
    }

    public static ErrorResponse of(int status, String message, Map<String, String> errors) {
        return new ErrorResponse(status, message, errors, LocalDateTime.now());
    }
}
