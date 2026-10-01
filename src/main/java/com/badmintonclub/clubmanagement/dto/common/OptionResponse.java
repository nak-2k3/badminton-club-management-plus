package com.badmintonclub.clubmanagement.dto.common;

import com.badmintonclub.clubmanagement.entity.Level;
import com.badmintonclub.clubmanagement.entity.Role;

// Dữ liệu cho ô chọn (select) trên giao diện: vai trò, trình độ...
public record OptionResponse(
        Long id,
        String name,
        String description
) {
    public static OptionResponse from(Role role) {
        return new OptionResponse(role.getId(), role.getRoleName(), role.getDescription());
    }

    public static OptionResponse from(Level level) {
        return new OptionResponse(level.getId(), level.getLevelName(), level.getDescription());
    }
}
