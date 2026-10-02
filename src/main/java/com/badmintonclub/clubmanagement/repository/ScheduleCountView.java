package com.badmintonclub.clubmanagement.repository;

// Projection kết quả "đếm theo buổi chơi" (group by schedule_id)
public interface ScheduleCountView {

    Long getScheduleId();

    Long getTotal();
}
