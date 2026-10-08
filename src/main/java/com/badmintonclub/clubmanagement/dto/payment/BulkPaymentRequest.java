package com.badmintonclub.clubmanagement.dto.payment;

import com.badmintonclub.clubmanagement.enums.PaymentMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// Thao tác hàng loạt trên các khoản đã chọn: xóa (bỏ qua paymentMethod) / thu tiền (bắt buộc paymentMethod)
public record BulkPaymentRequest(
        @NotEmpty(message = "Vui lòng chọn ít nhất 1 khoản thu")
        @Size(max = 500, message = "Chọn tối đa 500 khoản mỗi lần")
        List<@NotNull(message = "Khoản thu không hợp lệ") Long> ids,

        PaymentMethod paymentMethod
) {
}
