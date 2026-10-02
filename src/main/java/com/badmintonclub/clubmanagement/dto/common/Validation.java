package com.badmintonclub.clubmanagement.dto.common;

// Regex và thông báo dùng chung cho nhiều DTO
public final class Validation {

    // Số điện thoại Việt Nam: bắt đầu bằng 0, 10–11 chữ số; cho phép để trống
    public static final String PHONE_REGEX = "^$|^0\\d{9,10}$";
    public static final String PHONE_MESSAGE = "Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số";

    private Validation() {
    }
}
