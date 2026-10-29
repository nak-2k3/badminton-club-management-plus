package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.Expense;
import com.badmintonclub.clubmanagement.enums.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    boolean existsBySchedule_Id(Long scheduleId);

    // Mỗi buổi chỉ 1 khoản tiền sân + cầu (tránh ghi trùng)
    Optional<Expense> findFirstBySchedule_IdAndCategory(Long scheduleId, ExpenseCategory category);

    @EntityGraph(attributePaths = {"schedule", "createdBy"})
    Optional<Expense> findWithScheduleById(Long id);

    // Danh sách khoản chi: nạp sẵn buổi chơi, người ghi để map DTO
    @Override
    @EntityGraph(attributePaths = {"schedule", "createdBy"})
    Page<Expense> findAll(Specification<Expense> spec, Pageable pageable);
}
