package com.badmintonclub.clubmanagement.dto.expense;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.enums.ExpenseCategory;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

// Thêm / sửa khoản chi. Tiền sân + cầu (SESSION) bắt buộc gắn buổi chơi, mỗi buổi 1 khoản (kiểm tra ở ExpenseService).
public record ExpenseRequest(
        @NotBlank(message = "Vui lòng nhập nội dung khoản chi")
        @Size(max = 255, message = "Nội dung tối đa 255 ký tự")
        String description,

        @NotNull(message = "Vui lòng chọn loại chi")
        ExpenseCategory category,

        Long scheduleId,

        @NotNull(message = "Vui lòng nhập số tiền")
        @DecimalMin(value = "0", inclusive = false, message = "Số tiền phải lớn hơn 0")
        @Digits(integer = 10, fraction = 2, message = "Số tiền không hợp lệ")
        BigDecimal amount,

        @NotNull(message = "Vui lòng chọn ngày chi")
        @JsonFormat(pattern = DateFormats.DATE, lenient = OptBoolean.FALSE)
        LocalDate expenseDate,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note
) {
}
