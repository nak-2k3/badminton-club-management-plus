package com.badmintonclub.clubmanagement.security;

import org.springframework.security.oauth2.jwt.Jwt;

// Đọc thông tin người đang đăng nhập từ JWT (claim do JwtService tạo)
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        Object userId = jwt.getClaim("userId");
        return userId instanceof Number number ? number.longValue() : Long.valueOf(userId.toString());
    }
}
