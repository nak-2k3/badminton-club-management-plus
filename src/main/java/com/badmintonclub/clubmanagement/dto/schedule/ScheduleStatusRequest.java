package com.badmintonclub.clubmanagement.dto.schedule;

import com.badmintonclub.clubmanagement.enums.ScheduleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScheduleStatusRequest(
        @NotNull(message = "Vui lòng chọn trạng thái")
        ScheduleStatus status,

        // Lý do hủy buổi (tùy chọn), được ghi thêm vào ghi chú của buổi
        @Size(max = 200, message = "Lý do tối đa 200 ký tự")
        String reason
) {
}
