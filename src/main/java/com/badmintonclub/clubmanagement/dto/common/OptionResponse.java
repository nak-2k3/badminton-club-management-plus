package com.badmintonclub.clubmanagement.dto.common;

import com.badmintonclub.clubmanagement.entity.Level;
import com.badmintonclub.clubmanagement.entity.Role;
import com.badmintonclub.clubmanagement.entity.User;

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

    // Ô chọn thành viên: tên + email để phân biệt người trùng tên
    public static OptionResponse from(User user) {
        return new OptionResponse(user.getId(), user.getFullName(), user.getEmail());
    }
}
