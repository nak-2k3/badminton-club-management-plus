package com.badmintonclub.clubmanagement.dto.registration;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.entity.Attendance;
import com.badmintonclub.clubmanagement.entity.Registration;
import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.AttendanceStatus;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

// 1 thành viên đang đăng ký 1 buổi chơi, kèm kết quả điểm danh (không có email/SĐT: mọi thành viên đều xem được)
public record ParticipantResponse(
        Long userId,
        String fullName,
        Gender gender,
        String levelName,
        @JsonFormat(pattern = DateFormats.DATE_TIME) LocalDateTime registeredAt,
        AttendanceStatus attendanceStatus,
        @JsonFormat(pattern = DateFormats.DATE_TIME) LocalDateTime checkedAt,
        String checkedByName,
        String attendanceNote
) {
    // attendance = null: chưa điểm danh. Cần nạp sẵn user, user.level, attendance.checkedBy
    public static ParticipantResponse from(Registration registration, Attendance attendance) {
        User user = registration.getUser();
        return new ParticipantResponse(
                user.getId(),
                user.getFullName(),
                user.getGender(),
                user.getLevel() != null ? user.getLevel().getLevelName() : null,
                registration.getRegisteredAt(),
                attendance != null ? attendance.getStatus() : AttendanceStatus.NOT_MARKED,
                attendance != null ? attendance.getCheckedAt() : null,
                attendance != null && attendance.getCheckedBy() != null ? attendance.getCheckedBy().getFullName() : null,
                attendance != null ? attendance.getNote() : null);
    }
}
