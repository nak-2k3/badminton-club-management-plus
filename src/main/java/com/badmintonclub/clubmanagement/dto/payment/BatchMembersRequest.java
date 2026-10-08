package com.badmintonclub.clubmanagement.dto.payment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// Thêm người vào đợt thu (quên ai đó lúc tạo)
public record BatchMembersRequest(
        @NotEmpty(message = "Vui lòng chọn ít nhất 1 thành viên")
        @Size(max = 500, message = "Chọn tối đa 500 thành viên mỗi lần")
        List<@NotNull(message = "Thành viên không hợp lệ") Long> userIds
) {
}
