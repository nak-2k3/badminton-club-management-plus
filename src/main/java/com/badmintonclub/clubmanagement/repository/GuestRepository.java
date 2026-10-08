package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestRepository extends JpaRepository<Guest, Long> {

    // Khách quay lại (cùng SĐT) dùng lại hồ sơ cũ; phone không UNIQUE nên lấy bản mới nhất
    Optional<Guest> findFirstByPhoneOrderByIdDesc(String phone);
}
