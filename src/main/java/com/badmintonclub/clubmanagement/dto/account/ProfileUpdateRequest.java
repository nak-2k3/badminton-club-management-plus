package com.badmintonclub.clubmanagement.dto.account;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.dto.common.Validation;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

// Người dùng tự sửa thông tin của mình. Email, vai trò, trình độ, trạng thái, ngày tham gia do admin quản lý.
public record ProfileUpdateRequest(
        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @Pattern(regexp = Validation.PHONE_REGEX, message = Validation.PHONE_MESSAGE)
        String phone,

        @NotNull(message = "Vui lòng chọn giới tính")
        Gender gender,

        @Past(message = "Ngày sinh phải trước ngày hôm nay")
        @JsonFormat(pattern = DateFormats.DATE, lenient = OptBoolean.FALSE)
        LocalDate birthDate,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address
) {
}
