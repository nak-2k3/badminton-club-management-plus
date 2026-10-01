package com.badmintonclub.clubmanagement.dto.member;

import com.badmintonclub.clubmanagement.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record MemberStatusRequest(
        @NotNull(message = "Vui lòng chọn trạng thái")
        UserStatus status
) {
}
