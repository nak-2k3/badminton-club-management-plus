package com.badmintonclub.clubmanagement.dto.common;

import jakarta.validation.constraints.NotNull;

// Bật/tắt trạng thái hoạt động (sân, mức phí...)
public record ActiveRequest(
        @NotNull(message = "Vui lòng chọn trạng thái")
        Boolean active
) {
}
