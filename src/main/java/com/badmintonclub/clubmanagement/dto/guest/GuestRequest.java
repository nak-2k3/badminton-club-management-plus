package com.badmintonclub.clubmanagement.dto.guest;

import com.badmintonclub.clubmanagement.dto.common.Validation;
import com.badmintonclub.clubmanagement.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Dẫn 1 khách vào buổi chơi. Có SĐT trùng khách cũ thì dùng lại hồ sơ cũ.
public record GuestRequest(
        @NotBlank(message = "Vui lòng nhập họ tên khách")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @Pattern(regexp = Validation.PHONE_REGEX, message = Validation.PHONE_MESSAGE)
        String phone,

        // Không bắt buộc (guests.gender cho phép NULL)
        Gender gender,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note,

        // Chỉ ADMIN/TREASURER chọn người dẫn (null = khách tự liên hệ CLB); thành viên luôn là người dẫn
        Long invitedById
) {
}
