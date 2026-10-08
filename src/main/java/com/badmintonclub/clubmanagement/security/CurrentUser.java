package com.badmintonclub.clubmanagement.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;

// Đọc thông tin người đang đăng nhập từ JWT (claim do JwtService tạo)
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        Object userId = jwt.getClaim("userId");
        return userId instanceof Number number ? number.longValue() : Long.valueOf(userId.toString());
    }

    // Vai trò hiện tại (lấy từ DB mỗi request trong JwtUserAuthenticationConverter), vd hasAnyRole(auth, "ADMIN")
    public static boolean hasAnyRole(Authentication authentication, String... roles) {
        Set<String> wanted = Set.of(roles);
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .anyMatch(wanted::contains);
    }

    // Người quản lý thu chi: ADMIN hoặc TREASURER
    public static boolean isManager(Authentication authentication) {
        return hasAnyRole(authentication, "ADMIN", "TREASURER");
    }
}
