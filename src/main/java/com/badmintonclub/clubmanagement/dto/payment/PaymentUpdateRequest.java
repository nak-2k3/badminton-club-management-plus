package com.badmintonclub.clubmanagement.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Sửa 1 khoản chưa thu: ghi chú; số tiền chỉ sửa được với khoản thu thêm (vd giảm giá cho 1 người).
// Nội dung khoản thu sửa ở cấp đợt thu (PaymentBatchUpdateRequest).
public record PaymentUpdateRequest(
        @DecimalMin(value = "0", inclusive = false, message = "Số tiền phải lớn hơn 0")
        @Digits(integer = 10, fraction = 2, message = "Số tiền không hợp lệ")
        BigDecimal amount,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note
) {
}
