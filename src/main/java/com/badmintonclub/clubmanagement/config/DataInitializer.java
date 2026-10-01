package com.badmintonclub.clubmanagement.config;

import com.badmintonclub.clubmanagement.entity.Role;
import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.badmintonclub.clubmanagement.repository.RoleRepository;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// Tạo tài khoản ADMIN đầu tiên khi bảng users còn trống, để có thể đăng nhập lần đầu
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        Role adminRole = roleRepository.findByRoleName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Thiếu role ADMIN trong bảng roles (xem database/data.sql)"));

        User admin = new User();
        admin.setFullName("Quản trị viên");
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setGender(Gender.MALE);
        admin.setRole(adminRole);
        admin.setJoinDate(LocalDate.now());
        userRepository.save(admin);

        log.warn("Đã tạo tài khoản admin mặc định: {} — hãy đổi mật khẩu sau khi đăng nhập", adminEmail);
    }
}
