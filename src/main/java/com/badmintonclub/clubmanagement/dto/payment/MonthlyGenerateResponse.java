package com.badmintonclub.clubmanagement.dto.payment;

import java.math.BigDecimal;

// Kết quả tạo phí tháng hàng loạt: số khoản đã tạo, số người bỏ qua vì đã có phí tháng này,
// số người miễn phí (mức phí 0 ₫)
public record MonthlyGenerateResponse(
        Long batchId,
        int month,
        int year,
        int created,
        int skipped,
        int free,
        BigDecimal totalAmount
) {
}
