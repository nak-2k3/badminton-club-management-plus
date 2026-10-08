package com.badmintonclub.clubmanagement.dto.guest;

import com.badmintonclub.clubmanagement.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;

// Đánh dấu đã thu (PAID) / chưa thu (UNPAID) phí khách
public record PaymentStatusRequest(
        @NotNull(message = "Vui lòng chọn trạng thái thu tiền")
        PaymentStatus status
) {
}
