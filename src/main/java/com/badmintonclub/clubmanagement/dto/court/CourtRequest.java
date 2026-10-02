package com.badmintonclub.clubmanagement.dto.court;

import com.badmintonclub.clubmanagement.dto.common.Validation;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CourtRequest(
        @NotBlank(message = "Vui lòng nhập tên sân")
        @Size(max = 100, message = "Tên sân tối đa 100 ký tự")
        String courtName,

        @NotBlank(message = "Vui lòng nhập địa chỉ")
        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address,

        // Giá thuê 1 sân / 1 giờ
        @NotNull(message = "Vui lòng nhập giá thuê")
        @DecimalMin(value = "0", message = "Giá thuê không được âm")
        @Digits(integer = 10, fraction = 2, message = "Giá thuê không hợp lệ")
        BigDecimal hourlyRate,

        @Pattern(regexp = Validation.PHONE_REGEX, message = Validation.PHONE_MESSAGE)
        String phone,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note
) {
}
