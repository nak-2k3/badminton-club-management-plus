package com.badmintonclub.clubmanagement.dto.guest;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.entity.Guest;
import com.badmintonclub.clubmanagement.entity.GuestRegistration;
import com.badmintonclub.clubmanagement.entity.Schedule;
import com.badmintonclub.clubmanagement.enums.AttendanceStatus;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.badmintonclub.clubmanagement.enums.PaymentStatus;
import com.badmintonclub.clubmanagement.enums.ScheduleStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// 1 khách trong 1 buổi chơi. SĐT, phí, thu tiền chỉ trả cho người quản lý thu chi và người dẫn khách (full = true).
public record GuestRegistrationResponse(
        Long id,
        Long guestId,
        String fullName,
        String phone,
        Gender gender,
        String note,
        Long invitedById,
        String invitedByName,
        BigDecimal fee,
        PaymentStatus paymentStatus,
        @JsonFormat(pattern = DateFormats.DATE_TIME) LocalDateTime paidAt,
        String collectedByName,
        AttendanceStatus attendanceStatus,
        @JsonFormat(pattern = DateFormats.DATE_TIME) LocalDateTime registeredAt,
        Long scheduleId,
        String scheduleTitle,
        @JsonFormat(pattern = DateFormats.DATE) LocalDate playDate,
        @JsonFormat(pattern = DateFormats.TIME) LocalTime startTime,
        @JsonFormat(pattern = DateFormats.TIME) LocalTime endTime,
        ScheduleStatus scheduleStatus
) {
    // Cần nạp sẵn guest, invitedBy, collectedBy, schedule
    public static GuestRegistrationResponse from(GuestRegistration gr, boolean full) {
        Guest guest = gr.getGuest();
        Schedule schedule = gr.getSchedule();
        return new GuestRegistrationResponse(
                gr.getId(),
                guest.getId(),
                guest.getFullName(),
                full ? guest.getPhone() : null,
                guest.getGender(),
                full ? guest.getNote() : null,
                gr.getInvitedBy() != null ? gr.getInvitedBy().getId() : null,
                gr.getInvitedBy() != null ? gr.getInvitedBy().getFullName() : null,
                full ? gr.getFee() : null,
                full ? gr.getPaymentStatus() : null,
                full ? gr.getPaidAt() : null,
                full && gr.getCollectedBy() != null ? gr.getCollectedBy().getFullName() : null,
                gr.getAttendanceStatus(),
                gr.getRegisteredAt(),
                schedule.getId(),
                schedule.getTitle(),
                schedule.getPlayDate(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getStatus());
    }
}
