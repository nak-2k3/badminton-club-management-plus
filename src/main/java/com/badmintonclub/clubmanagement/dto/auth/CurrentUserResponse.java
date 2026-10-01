package com.badmintonclub.clubmanagement.dto.auth;

import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.Gender;

public record CurrentUserResponse(
        Long id,
        String fullName,
        String email,
        Gender gender,
        String role,
        String avatar
) {
    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getGender(),
                user.getRole().getRoleName(),
                user.getAvatar()
        );
    }
}
