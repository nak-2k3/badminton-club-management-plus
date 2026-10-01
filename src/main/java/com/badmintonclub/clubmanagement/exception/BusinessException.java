package com.badmintonclub.clubmanagement.exception;

// Vi phạm quy tắc nghiệp vụ (vd: buổi chơi đã đủ người, đã thu học phí tháng này...) -> HTTP 400
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
