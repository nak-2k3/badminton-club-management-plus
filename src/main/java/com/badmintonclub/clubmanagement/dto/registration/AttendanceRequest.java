package com.badmintonclub.clubmanagement.dto.registration;

import com.badmintonclub.clubmanagement.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Điểm danh 1 người; status = NOT_MARKED để bỏ điểm danh (đánh dấu nhầm)
public record AttendanceRequest(
        @NotNull(message = "Vui lòng chọn trạng thái điểm danh")
        AttendanceStatus status,

        // null: giữ ghi chú cũ; "" : xóa ghi chú
        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note
) {
}
