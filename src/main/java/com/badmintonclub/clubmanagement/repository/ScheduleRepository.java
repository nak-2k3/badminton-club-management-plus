package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // Sân đã có lịch chơi thì không được xóa (FK schedules.court_id)
    boolean existsByCourt_Id(Long courtId);
}
