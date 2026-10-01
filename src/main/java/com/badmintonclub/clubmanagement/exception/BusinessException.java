package com.badmintonclub.clubmanagement.exception;

import java.util.Map;

// Vi phạm quy tắc nghiệp vụ (vd: buổi chơi đã đủ người, đã thu học phí tháng này...) -> HTTP 400
public class BusinessException extends RuntimeException {

    // Lỗi gắn với trường cụ thể (vd email trùng) để frontend hiện dưới ô nhập; null nếu lỗi chung
    private final Map<String, String> errors;

    public BusinessException(String message) {
        super(message);
        this.errors = null;
    }

    public BusinessException(String field, String message) {
        super(message);
        this.errors = Map.of(field, message);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
