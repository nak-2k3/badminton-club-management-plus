package com.badmintonclub.clubmanagement.dto.member;

import com.badmintonclub.clubmanagement.enums.Gender;
import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.dto.common.Validation;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

// Sửa thông tin thành viên (mật khẩu và trạng thái đổi qua API riêng)
public record MemberUpdateRequest(
        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @NotBlank(message = "Vui lòng nhập email")
        @Email(message = "Email không hợp lệ")
        @Size(max = 100, message = "Email tối đa 100 ký tự")
        String email,

        @Pattern(regexp = Validation.PHONE_REGEX, message = Validation.PHONE_MESSAGE)
        String phone,

        @NotNull(message = "Vui lòng chọn giới tính")
        Gender gender,

        @Past(message = "Ngày sinh phải trước ngày hôm nay")
        @JsonFormat(pattern = DateFormats.DATE, lenient = OptBoolean.FALSE)
        LocalDate birthDate,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address,

        @NotNull(message = "Vui lòng chọn vai trò")
        Long roleId,

        Long levelId,

        @NotNull(message = "Vui lòng chọn ngày tham gia")
        @PastOrPresent(message = "Ngày tham gia không được ở tương lai")
        @JsonFormat(pattern = DateFormats.DATE, lenient = OptBoolean.FALSE)
        LocalDate joinDate
) {
}
