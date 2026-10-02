package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.account.ChangePasswordRequest;
import com.badmintonclub.clubmanagement.dto.account.ProfileUpdateRequest;
import com.badmintonclub.clubmanagement.dto.member.MemberResponse;
import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.exception.BusinessException;
import com.badmintonclub.clubmanagement.exception.ResourceNotFoundException;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// "Tài khoản của tôi": mọi vai trò tự xem/sửa thông tin cá nhân và đổi mật khẩu
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MemberResponse getProfile(Long userId) {
        return MemberResponse.from(findUser(userId));
    }

    @Transactional
    public MemberResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = findUser(userId);
        String phone = trimToNull(request.phone());
        if (phone != null && userRepository.existsByPhoneAndIdNot(phone, userId)) {
            throw new BusinessException("phone", "Số điện thoại " + phone + " đã được sử dụng");
        }

        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        user.setGender(request.gender());
        user.setBirthDate(request.birthDate());
        user.setAddress(trimToNull(request.address()));
        return MemberResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException("currentPassword", "Mật khẩu hiện tại không đúng");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("newPassword", "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
    }

    private User findUser(Long userId) {
        return userRepository.findWithRoleAndLevelById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
