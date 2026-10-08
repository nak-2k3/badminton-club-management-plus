package com.badmintonclub.clubmanagement.dto.guest;

import com.badmintonclub.clubmanagement.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

// Điểm danh khách (guest_registrations không có cột ghi chú / người điểm danh)
public record GuestAttendanceRequest(
        @NotNull(message = "Vui lòng chọn trạng thái điểm danh")
        AttendanceStatus status
) {
}
