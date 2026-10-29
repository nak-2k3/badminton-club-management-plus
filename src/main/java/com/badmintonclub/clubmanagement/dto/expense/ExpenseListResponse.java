package com.badmintonclub.clubmanagement.dto.expense;

import com.badmintonclub.clubmanagement.dto.common.PageResponse;
import com.badmintonclub.clubmanagement.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.util.List;

// 1 trang khoản chi + tổng chi theo từng loại (đủ các loại, kể cả 0) theo bộ lọc, không theo bộ lọc loại
public record ExpenseListResponse(
        PageResponse<ExpenseResponse> page,
        long totalCount,
        BigDecimal totalAmount,
        List<CategoryTotal> byCategory
) {
    public record CategoryTotal(ExpenseCategory category, long count, BigDecimal amount) {
    }
}
