package com.badmintonclub.clubmanagement.dto.payment;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.entity.PaymentBatch;
import com.badmintonclub.clubmanagement.enums.PaymentType;
import com.badmintonclub.clubmanagement.repository.BatchStatsView;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 1 đợt thu kèm thống kê: số người, số đã nộp, tiền đã thu / tổng
public record PaymentBatchResponse(
        Long id,
        PaymentType paymentType,
        String description,
        Integer month,
        Integer year,
        BigDecimal amount,
        String note,
        String createdByName,
        @JsonFormat(pattern = DateFormats.DATE_TIME) LocalDateTime createdAt,
        long memberCount,
        long paidCount,
        BigDecimal paidAmount,
        BigDecimal totalAmount
) {
    // Cần nạp sẵn createdBy; stats = null khi đợt chưa có khoản nào
    public static PaymentBatchResponse from(PaymentBatch batch, BatchStatsView stats) {
        return new PaymentBatchResponse(
                batch.getId(),
                batch.getPaymentType(),
                batch.getDescription(),
                batch.getMonth(),
                batch.getYear(),
                batch.getAmount(),
                batch.getNote(),
                batch.getCreatedBy().getFullName(),
                batch.getCreatedAt(),
                stats != null ? stats.getMemberCount() : 0,
                stats != null ? stats.getPaidCount() : 0,
                stats != null ? stats.getPaidAmount() : BigDecimal.ZERO,
                stats != null ? stats.getTotalAmount() : BigDecimal.ZERO);
    }
}
