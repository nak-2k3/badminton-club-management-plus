package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.GuestRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface GuestRegistrationRepository extends JpaRepository<GuestRegistration, Long> {

    // Số khách theo từng buổi (khách không có trạng thái hủy: có dòng là chiếm 1 chỗ)
    @Query("""
            select g.schedule.id as scheduleId, count(g) as total from GuestRegistration g
            where g.schedule.id in :scheduleIds
            group by g.schedule.id""")
    List<ScheduleCountView> countBySchedules(@Param("scheduleIds") Collection<Long> scheduleIds);

    boolean existsBySchedule_Id(Long scheduleId);
}
