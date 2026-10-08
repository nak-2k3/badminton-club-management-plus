package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = {"role", "level"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"role", "level"})
    Optional<User> findWithRoleAndLevelById(Long id);

    // Danh sách thành viên: nạp sẵn role, level để map DTO
    @Override
    @EntityGraph(attributePaths = {"role", "level"})
    Page<User> findAll(Specification<User> spec, Pageable pageable);

    // Gọi ở mỗi request có token (JwtUserAuthenticationConverter) -> chỉ lấy 2 cột cần thiết
    @Query("select u.status as status, r.roleName as roleName from User u join u.role r where u.id = :id")
    Optional<UserAuthView> findAuthViewById(@Param("id") Long id);

    // Tạo phí tháng hàng loạt: mọi thành viên theo trạng thái
    List<User> findByStatus(UserStatus status);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);
}
