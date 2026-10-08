package com.badmintonclub.clubmanagement.dto.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Tạo phí tháng cho mọi thành viên đang hoạt động
public record MonthlyGenerateRequest(
        @NotNull(message = "Vui lòng chọn tháng")
        @Min(value = 1, message = "Tháng không hợp lệ")
        @Max(value = 12, message = "Tháng không hợp lệ")
        Integer month,

        @NotNull(message = "Vui lòng chọn năm")
        @Min(value = 2000, message = "Năm không hợp lệ")
        @Max(value = 2100, message = "Năm không hợp lệ")
        Integer year
) {
}
