package com.badmintonclub.clubmanagement.dto.registration;

import jakarta.validation.constraints.NotNull;

// ADMIN thêm 1 thành viên vào buổi chơi
public record AddParticipantRequest(
        @NotNull(message = "Vui lòng chọn thành viên")
        Long userId
) {
}
