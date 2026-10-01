package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.auth.CurrentUserResponse;
import com.badmintonclub.clubmanagement.dto.auth.LoginRequest;
import com.badmintonclub.clubmanagement.dto.auth.LoginResponse;
import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.exception.ResourceNotFoundException;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import com.badmintonclub.clubmanagement.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // Sai email/mật khẩu hoặc tài khoản bị khóa -> ném AuthenticationException
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));

        User user = findByEmail(request.email());
        return new LoginResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getExpirationSeconds(),
                CurrentUserResponse.from(user));
    }

    // Tìm theo userId trong token (không theo email, vì email có thể được admin sửa)
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long userId) {
        return userRepository.findWithRoleAndLevelById(userId)
                .map(CurrentUserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }
}
