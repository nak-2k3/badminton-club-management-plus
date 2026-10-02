package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.Registration;
import com.badmintonclub.clubmanagement.enums.RegistrationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    // Số thành viên đăng ký theo từng buổi (1 truy vấn cho cả trang danh sách)
    @Query("""
            select r.schedule.id as scheduleId, count(r) as total from Registration r
            where r.schedule.id in :scheduleIds and r.status = :status
            group by r.schedule.id""")
    List<ScheduleCountView> countBySchedules(@Param("scheduleIds") Collection<Long> scheduleIds,
                                             @Param("status") RegistrationStatus status);

    // Trong các buổi scheduleIds, những buổi mà userId đang đăng ký
    @Query("""
            select r.schedule.id from Registration r
            where r.user.id = :userId and r.schedule.id in :scheduleIds and r.status = :status""")
    List<Long> findScheduleIdsOfUser(@Param("userId") Long userId,
                                     @Param("scheduleIds") Collection<Long> scheduleIds,
                                     @Param("status") RegistrationStatus status);

    // Mỗi user chỉ có 1 dòng cho 1 buổi (unique uk_user_schedule): hủy rồi đăng ký lại dùng lại dòng cũ
    Optional<Registration> findBySchedule_IdAndUser_Id(Long scheduleId, Long userId);

    // Danh sách người tham gia: nạp sẵn user, trình độ
    @EntityGraph(attributePaths = {"user", "user.level"})
    List<Registration> findBySchedule_IdAndStatusOrderByRegisteredAtAscIdAsc(Long scheduleId, RegistrationStatus status);

    long countBySchedule_IdAndStatus(Long scheduleId, RegistrationStatus status);

    // Xóa mọi đăng ký của buổi (dùng khi xóa buổi: lúc đó chỉ còn đăng ký đã hủy)
    @Modifying
    @Query("delete from Registration r where r.schedule.id = :scheduleId")
    int deleteBySchedule(@Param("scheduleId") Long scheduleId);
}
