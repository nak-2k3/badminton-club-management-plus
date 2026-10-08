package com.badmintonclub.clubmanagement.dto.payment;

import com.badmintonclub.clubmanagement.dto.common.PageResponse;

import java.math.BigDecimal;

// Danh sách đợt thu (1 trang) + tổng đã thu / chưa thu của mọi khoản trong các đợt khớp bộ lọc
public record PaymentBatchListResponse(
        PageResponse<PaymentBatchResponse> page,
        long paidCount,
        BigDecimal paidAmount,
        long unpaidCount,
        BigDecimal unpaidAmount
) {
}
