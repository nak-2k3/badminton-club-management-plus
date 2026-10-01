package com.badmintonclub.clubmanagement.security;

import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.UserStatus;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// Đăng nhập bằng email; quyền lấy từ roles.role_name
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản: " + email));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().getRoleName())
                .disabled(user.getStatus() == UserStatus.INACTIVE)
                .accountLocked(user.getStatus() == UserStatus.LOCKED)
                .build();
    }
}
