package com.badmintonclub.clubmanagement.dto.registration;

import com.badmintonclub.clubmanagement.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

// Đánh dấu nhanh mọi người CHƯA điểm danh của buổi (PRESENT hoặc ABSENT)
public record MarkAllAttendanceRequest(
        @NotNull(message = "Vui lòng chọn trạng thái điểm danh")
        AttendanceStatus status
) {
}
