package com.badmintonclub.clubmanagement.dto.guest;

import com.badmintonclub.clubmanagement.dto.common.PageResponse;

import java.math.BigDecimal;

// Trang "Phí khách": 1 trang danh sách + tổng đã thu / chưa thu theo đúng bộ lọc (mọi trang)
public record GuestFeeListResponse(
        PageResponse<GuestRegistrationResponse> page,
        long paidCount,
        BigDecimal paidAmount,
        long unpaidCount,
        BigDecimal unpaidAmount
) {
}
