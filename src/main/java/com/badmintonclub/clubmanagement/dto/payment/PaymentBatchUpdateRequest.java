package com.badmintonclub.clubmanagement.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Sửa đợt thu: nội dung, ghi chú; số tiền (chỉ đợt thu thêm) áp dụng cho mọi người CHƯA nộp trong đợt
public record PaymentBatchUpdateRequest(
        @NotBlank(message = "Vui lòng nhập nội dung khoản thu")
        @Size(max = 255, message = "Nội dung tối đa 255 ký tự")
        String description,

        @DecimalMin(value = "0", inclusive = false, message = "Số tiền phải lớn hơn 0")
        @Digits(integer = 10, fraction = 2, message = "Số tiền không hợp lệ")
        BigDecimal amount,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note
) {
}
