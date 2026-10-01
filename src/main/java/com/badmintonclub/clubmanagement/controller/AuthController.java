package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.auth.CurrentUserResponse;
import com.badmintonclub.clubmanagement.dto.auth.LoginRequest;
import com.badmintonclub.clubmanagement.dto.auth.LoginResponse;
import com.badmintonclub.clubmanagement.security.CurrentUser;
import com.badmintonclub.clubmanagement.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // Thông tin người đang đăng nhập (frontend gọi khi tải lại trang)
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.getCurrentUser(CurrentUser.id(jwt));
    }
}
