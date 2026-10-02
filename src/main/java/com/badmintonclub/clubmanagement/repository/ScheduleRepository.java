package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.Schedule;
import com.badmintonclub.clubmanagement.enums.ScheduleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long>, JpaSpecificationExecutor<Schedule> {

    // Sân đã có lịch chơi thì không được xóa (FK schedules.court_id)
    boolean existsByCourt_Id(Long courtId);

    // Danh sách buổi chơi: nạp sẵn sân, người tạo để map DTO
    @Override
    @EntityGraph(attributePaths = {"court", "createdBy"})
    Page<Schedule> findAll(Specification<Schedule> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"court", "createdBy"})
    Optional<Schedule> findWithCourtById(Long id);

    // Khóa dòng buổi chơi tới hết transaction: 2 người đăng ký cùng lúc không vượt quá số chỗ
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.id = :id")
    Optional<Schedule> findByIdForUpdate(@Param("id") Long id);

    // Buổi khác (bỏ qua buổi excludeId và buổi đã hủy) cùng địa điểm, cùng ngày, có khung giờ giao nhau
    @Query("""
            select s from Schedule s
            where s.court.id = :courtId and s.playDate = :playDate and s.status <> :ignoredStatus
              and s.startTime < :endTime and s.endTime > :startTime and s.id <> :excludeId
            order by s.startTime""")
    List<Schedule> findOverlapping(@Param("courtId") Long courtId,
                                   @Param("playDate") LocalDate playDate,
                                   @Param("startTime") LocalTime startTime,
                                   @Param("endTime") LocalTime endTime,
                                   @Param("excludeId") Long excludeId,
                                   @Param("ignoredStatus") ScheduleStatus ignoredStatus);
}
