package com.badmintonclub.clubmanagement.dto.report;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.enums.ExpenseCategory;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo thu chi 1 kỳ. Thu tính theo ngày thu tiền (paid_at), chi theo ngày chi (expense_date).
 * Tồn cuối kỳ = tồn đầu kỳ (thu − chi trước kỳ) + thu − chi. Công nợ là số chưa thu tại thời điểm xem (không theo kỳ).
 */
public record ReportSummaryResponse(
        @JsonFormat(pattern = DateFormats.DATE) LocalDate from,
        @JsonFormat(pattern = DateFormats.DATE) LocalDate to,
        BigDecimal openingBalance,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal closingBalance,
        List<IncomeLine> income,
        List<ExpenseLine> expense,
        List<MonthPoint> months,
        Outstanding outstanding
) {
    // source: MONTHLY (phí tháng), EXTRA (thu thêm), GUEST (phí khách)
    public record IncomeLine(String source, long count, BigDecimal amount) {
    }

    public record ExpenseLine(ExpenseCategory category, long count, BigDecimal amount) {
    }

    public record MonthPoint(int year, int month, BigDecimal income, BigDecimal expense) {
    }

    // userId = null: khách tự liên hệ CLB (không có người dẫn)
    public record Debtor(Long userId, String name, String email, long paymentCount, BigDecimal paymentAmount,
                         long guestCount, BigDecimal guestAmount, BigDecimal total) {
    }

    public record Outstanding(long paymentCount, BigDecimal paymentAmount, long guestCount, BigDecimal guestAmount,
                              BigDecimal total, List<Debtor> debtors) {
    }
}
