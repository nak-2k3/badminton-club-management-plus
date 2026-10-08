package com.badmintonclub.clubmanagement.dto.payment;

import com.badmintonclub.clubmanagement.enums.PaymentMethod;
import com.badmintonclub.clubmanagement.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;

// Đánh dấu đã thu (PAID, bắt buộc hình thức) / hoàn tác về chưa thu (UNPAID, bỏ qua hình thức)
public record CollectPaymentRequest(
        @NotNull(message = "Vui lòng chọn trạng thái thu tiền")
        PaymentStatus status,

        PaymentMethod paymentMethod
) {
}
