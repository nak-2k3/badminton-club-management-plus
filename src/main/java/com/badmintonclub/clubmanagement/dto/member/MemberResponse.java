package com.badmintonclub.clubmanagement.dto.member;

import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.badmintonclub.clubmanagement.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Gender gender,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate birthDate,
        String address,
        Long roleId,
        String roleName,
        Long levelId,
        String levelName,
        @JsonFormat(pattern = "dd/MM/yyyy") LocalDate joinDate,
        UserStatus status,
        String avatar,
        @JsonFormat(pattern = "dd/MM/yyyy HH:mm") LocalDateTime createdAt
) {
    // Cần nạp sẵn role, level (@EntityGraph) trước khi gọi
    public static MemberResponse from(User user) {
        return new MemberResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getGender(),
                user.getBirthDate(),
                user.getAddress(),
                user.getRole().getId(),
                user.getRole().getRoleName(),
                user.getLevel() != null ? user.getLevel().getId() : null,
                user.getLevel() != null ? user.getLevel().getLevelName() : null,
                user.getJoinDate(),
                user.getStatus(),
                user.getAvatar(),
                user.getCreatedAt());
    }
}
