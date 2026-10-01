package com.badmintonclub.clubmanagement.dto.member;

final class MemberValidation {

    // Số điện thoại Việt Nam: bắt đầu bằng 0, 10–11 chữ số; cho phép để trống
    static final String PHONE_REGEX = "^$|^0\\d{9,10}$";
    static final String PHONE_MESSAGE = "Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số";

    private MemberValidation() {
    }
}
