package com.badmintonclub.clubmanagement.dto.expense;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.entity.Expense;
import com.badmintonclub.clubmanagement.entity.Schedule;
import com.badmintonclub.clubmanagement.enums.ExpenseCategory;
import com.badmintonclub.clubmanagement.enums.ScheduleStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpenseResponse(
        Long id,
        String description,
        ExpenseCategory category,
        BigDecimal amount,
        @JsonFormat(pattern = DateFormats.DATE) LocalDate expenseDate,
        Long scheduleId,
        String scheduleTitle,
        @JsonFormat(pattern = DateFormats.DATE) LocalDate playDate,
        ScheduleStatus scheduleStatus,
        String createdByName,
        @JsonFormat(pattern = DateFormats.DATE_TIME) LocalDateTime createdAt,
        String note
) {
    // Cần nạp sẵn schedule, createdBy
    public static ExpenseResponse from(Expense expense) {
        Schedule schedule = expense.getSchedule();
        return new ExpenseResponse(
                expense.getId(),
                expense.getDescription(),
                expense.getCategory(),
                expense.getAmount(),
                expense.getExpenseDate(),
                schedule != null ? schedule.getId() : null,
                schedule != null ? schedule.getTitle() : null,
                schedule != null ? schedule.getPlayDate() : null,
                schedule != null ? schedule.getStatus() : null,
                expense.getCreatedBy().getFullName(),
                expense.getCreatedAt(),
                expense.getNote());
    }
}
