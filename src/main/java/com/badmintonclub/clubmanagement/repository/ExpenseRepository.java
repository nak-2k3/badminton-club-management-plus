package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    boolean existsBySchedule_Id(Long scheduleId);
}
