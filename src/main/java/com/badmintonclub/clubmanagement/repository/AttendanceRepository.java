package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.Attendance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findBySchedule_IdAndUser_Id(Long scheduleId, Long userId);

    @EntityGraph(attributePaths = {"user", "checkedBy"})
    List<Attendance> findBySchedule_Id(Long scheduleId);

    boolean existsBySchedule_Id(Long scheduleId);
}
