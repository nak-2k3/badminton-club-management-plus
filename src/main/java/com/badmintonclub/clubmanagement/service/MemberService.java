package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.common.PageResponse;
import com.badmintonclub.clubmanagement.dto.member.MemberCreateRequest;
import com.badmintonclub.clubmanagement.dto.member.MemberResponse;
import com.badmintonclub.clubmanagement.dto.member.MemberUpdateRequest;
import com.badmintonclub.clubmanagement.entity.Level;
import com.badmintonclub.clubmanagement.entity.Role;
import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.badmintonclub.clubmanagement.enums.UserStatus;
import com.badmintonclub.clubmanagement.exception.BusinessException;
import com.badmintonclub.clubmanagement.exception.ResourceNotFoundException;
import com.badmintonclub.clubmanagement.repository.LevelRepository;
import com.badmintonclub.clubmanagement.repository.RoleRepository;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberService {

    // Chỉ cho sắp xếp theo các cột này (tránh lỗi khi frontend gửi tên cột lạ)
    private static final Set<String> SORTABLE_FIELDS = Set.of("fullName", "email", "birthDate", "joinDate", "createdAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LevelRepository levelRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> search(String keyword, UserStatus status, Long roleId, Long levelId,
                                               Gender gender, int page, int size, String sort, String direction) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                // Collation utf8mb4_unicode_ci: không phân biệt hoa thường và dấu ("nguyen" khớp "Nguyễn")
                String pattern = "%" + escapeLike(keyword.trim()) + "%";
                predicates.add(cb.or(
                        cb.like(root.get("fullName"), pattern, '\\'),
                        cb.like(root.get("email"), pattern, '\\'),
                        cb.like(root.get("phone"), pattern, '\\')));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (roleId != null) predicates.add(cb.equal(root.get("role").get("id"), roleId));
            if (levelId != null) predicates.add(cb.equal(root.get("level").get("id"), levelId));
            if (gender != null) predicates.add(cb.equal(root.get("gender"), gender));
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        String sortField = SORTABLE_FIELDS.contains(sort) ? sort : "createdAt";
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(sortDirection, sortField).and(Sort.by(Sort.Direction.DESC, "id")));

        return PageResponse.from(userRepository.findAll(spec, pageable), MemberResponse::from);
    }

    @Transactional(readOnly = true)
    public MemberResponse getById(Long id) {
        return MemberResponse.from(findMember(id));
    }

    @Transactional
    public MemberResponse create(MemberCreateRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("email", "Email " + email + " đã được sử dụng");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new BusinessException("phone", "Số điện thoại " + phone + " đã được sử dụng");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhone(phone);
        user.setGender(request.gender());
        user.setBirthDate(request.birthDate());
        user.setAddress(trimToNull(request.address()));
        user.setRole(findRole(request.roleId()));
        user.setLevel(findLevel(request.levelId()));
        user.setJoinDate(request.joinDate() != null ? request.joinDate() : LocalDate.now());

        return MemberResponse.from(userRepository.save(user));
    }

    @Transactional
    public MemberResponse update(Long id, MemberUpdateRequest request, Long currentUserId) {
        User user = findMember(id);
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new BusinessException("email", "Email " + email + " đã được sử dụng");
        }
        if (phone != null && userRepository.existsByPhoneAndIdNot(phone, id)) {
            throw new BusinessException("phone", "Số điện thoại " + phone + " đã được sử dụng");
        }
        // Tránh admin tự hạ quyền rồi không vào lại được trang quản trị
        if (id.equals(currentUserId) && !user.getRole().getId().equals(request.roleId())) {
            throw new BusinessException("roleId", "Bạn không thể tự thay đổi vai trò của chính mình");
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setGender(request.gender());
        user.setBirthDate(request.birthDate());
        user.setAddress(trimToNull(request.address()));
        user.setRole(findRole(request.roleId()));
        user.setLevel(findLevel(request.levelId()));
        user.setJoinDate(request.joinDate());

        return MemberResponse.from(user);
    }

    // Không xóa cứng thành viên: chuyển sang INACTIVE (ngừng hoạt động) hoặc LOCKED (khóa)
    @Transactional
    public MemberResponse changeStatus(Long id, UserStatus status, Long currentUserId) {
        if (id.equals(currentUserId) && status != UserStatus.ACTIVE) {
            throw new BusinessException("Bạn không thể tự khóa hoặc ngừng hoạt động tài khoản của chính mình");
        }
        User user = findMember(id);
        user.setStatus(status);
        return MemberResponse.from(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = findMember(id);
        user.setPassword(passwordEncoder.encode(newPassword));
    }

    private User findMember(Long id) {
        return userRepository.findWithRoleAndLevelById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thành viên"));
    }

    private Role findRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException("roleId", "Vai trò không tồn tại"));
    }

    private Level findLevel(Long levelId) {
        if (levelId == null) return null;
        return levelRepository.findById(levelId)
                .orElseThrow(() -> new BusinessException("levelId", "Trình độ không tồn tại"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    // Cột phone UNIQUE: để trống phải lưu NULL, không lưu chuỗi rỗng
    private static String normalizePhone(String phone) {
        return trimToNull(phone);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
