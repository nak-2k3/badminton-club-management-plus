package com.badmintonclub.clubmanagement.dto.payment;

import com.badmintonclub.clubmanagement.dto.common.PageResponse;

import java.math.BigDecimal;

// Trang "Khoản thu": 1 trang danh sách + tổng đã thu / chưa thu theo đúng bộ lọc (mọi trang)
public record PaymentListResponse(
        PageResponse<PaymentResponse> page,
        long paidCount,
        BigDecimal paidAmount,
        long unpaidCount,
        BigDecimal unpaidAmount
) {
}
