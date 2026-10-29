package com.badmintonclub.clubmanagement.dto.schedule;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.entity.Court;
import com.badmintonclub.clubmanagement.entity.Schedule;
import com.badmintonclub.clubmanagement.enums.ScheduleStatus;
import com.badmintonclub.clubmanagement.enums.ScheduleType;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ScheduleResponse(
        Long id,
        String title,
        ScheduleType scheduleType,
        Long courtId,
        String courtName,
        String courtAddress,
        boolean courtActive,
        Integer courtCount,
        @JsonFormat(pattern = DateFormats.DATE) LocalDate playDate,
        @JsonFormat(pattern = DateFormats.TIME) LocalTime startTime,
        @JsonFormat(pattern = DateFormats.TIME) LocalTime endTime,
        Integer maxPlayers,
        // Thành viên đang đăng ký + khách
        long registeredCount,
        ScheduleStatus status,
        // Đã tới giờ bắt đầu: không nhận đăng ký nữa dù status vẫn OPEN
        boolean started,
        // Người đang xem đã đăng ký buổi này chưa
        boolean registeredByMe,
        // Hạn chót thành viên tự hủy đăng ký (giờ bắt đầu − RegistrationService.SELF_CANCEL_DEADLINE_HOURS)
        @JsonFormat(pattern = DateFormats.DATE_TIME) LocalDateTime selfCancelDeadline,
        BigDecimal hourlyRate,
        // Tiền sân dự kiến = giá/giờ × số giờ × số sân (giá hiện tại của sân)
        BigDecimal estimatedCourtCost,
        Long createdById,
        String createdByName,
        @JsonFormat(pattern = DateFormats.DATE_TIME) LocalDateTime createdAt,
        String note,
        // Lý do hủy (tách từ note) — chỉ có khi buổi đã hủy và người hủy có nhập lý do
        String cancelReason
) {
    // Lý do hủy được ghi thêm vào note dạng "... | Lý do hủy: <lý do>" (ScheduleService.changeStatus)
    public static final String CANCEL_REASON_PREFIX = "Lý do hủy: ";

    // Cần nạp sẵn court, createdBy (@EntityGraph) trước khi gọi
    public static ScheduleResponse from(Schedule schedule, long registeredCount, boolean started,
                                        boolean registeredByMe, LocalDateTime selfCancelDeadline,
                                        BigDecimal estimatedCourtCost) {
        Court court = schedule.getCourt();
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getTitle(),
                schedule.getScheduleType(),
                court.getId(),
                court.getCourtName(),
                court.getAddress(),
                Boolean.TRUE.equals(court.getActive()),
                schedule.getCourtCount(),
                schedule.getPlayDate(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getMaxPlayers(),
                registeredCount,
                schedule.getStatus(),
                started,
                registeredByMe,
                selfCancelDeadline,
                court.getHourlyRate(),
                estimatedCourtCost,
                schedule.getCreatedBy().getId(),
                schedule.getCreatedBy().getFullName(),
                schedule.getCreatedAt(),
                schedule.getNote(),
                cancelReason(schedule));
    }

    private static String cancelReason(Schedule schedule) {
        String note = schedule.getNote();
        if (schedule.getStatus() != ScheduleStatus.CANCELLED || note == null) return null;
        int index = note.lastIndexOf(CANCEL_REASON_PREFIX);
        if (index < 0) return null;
        String reason = note.substring(index + CANCEL_REASON_PREFIX.length()).trim();
        return reason.isEmpty() ? null : reason;
    }
}
