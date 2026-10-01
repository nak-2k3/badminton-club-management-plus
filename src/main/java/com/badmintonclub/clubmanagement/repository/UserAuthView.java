package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.enums.UserStatus;

// Projection nhẹ dùng khi kiểm tra token ở mỗi request: chỉ lấy trạng thái và vai trò hiện tại
public interface UserAuthView {

    UserStatus getStatus();

    String getRoleName();
}
