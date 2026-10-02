package com.badmintonclub.clubmanagement.dto.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Đổi email đăng nhập của chính mình: bắt buộc xác nhận bằng mật khẩu hiện tại
public record ChangeEmailRequest(
        @NotBlank(message = "Vui lòng nhập email mới")
        @Email(message = "Email không hợp lệ")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String newEmail,

        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        String currentPassword
) {
}
