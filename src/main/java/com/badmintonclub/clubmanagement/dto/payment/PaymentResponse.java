package com.badmintonclub.clubmanagement.dto.payment;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.entity.Payment;
import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.PaymentMethod;
import com.badmintonclub.clubmanagement.enums.PaymentStatus;
import com.badmintonclub.clubmanagement.enums.PaymentType;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long batchId,
        Long userId,
        String userName,
        String userEmail,
        PaymentType paymentType,
        String description,
        Integer month,
        Integer year,
        BigDecimal amount,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        @JsonFormat(pattern = DateFormats.DATE_TIME) LocalDateTime paidAt,
        String createdByName,
        @JsonFormat(pattern = DateFormats.DATE_TIME) LocalDateTime createdAt,
        String note
) {
    // Cần nạp sẵn user, createdBy, batch
    public static PaymentResponse from(Payment payment) {
        User user = payment.getUser();
        return new PaymentResponse(
                payment.getId(),
                payment.getBatch().getId(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                payment.getPaymentType(),
                payment.getBatch().getDescription(),
                payment.getMonth(),
                payment.getYear(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getPaidAt(),
                payment.getCreatedBy().getFullName(),
                payment.getCreatedAt(),
                payment.getNote());
    }
}
