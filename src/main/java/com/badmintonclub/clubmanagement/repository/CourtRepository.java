package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.Court;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourtRepository extends JpaRepository<Court, Long> {

    // Collation utf8mb4_unicode_ci: so sánh không phân biệt hoa thường / dấu
    boolean existsByCourtName(String courtName);

    boolean existsByCourtNameAndIdNot(String courtName, Long id);
}
