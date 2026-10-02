package com.badmintonclub.clubmanagement.dto.fee;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.enums.FeeType;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

// MONTHLY bắt buộc có gender; GUEST luôn lưu gender = null (kiểm tra ở FeeSettingService)
public record FeeSettingRequest(
        @NotNull(message = "Vui lòng chọn loại phí")
        FeeType feeType,

        Gender gender,

        @NotNull(message = "Vui lòng nhập số tiền")
        @DecimalMin(value = "0", message = "Số tiền không được âm")
        @Digits(integer = 10, fraction = 2, message = "Số tiền không hợp lệ")
        BigDecimal amount,

        @NotNull(message = "Vui lòng chọn ngày áp dụng")
        @JsonFormat(pattern = DateFormats.DATE, lenient = OptBoolean.FALSE)
        LocalDate effectiveFrom
) {
}
