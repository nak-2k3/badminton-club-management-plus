package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.Guest;
import com.badmintonclub.clubmanagement.entity.GuestRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GuestRegistrationRepository extends JpaRepository<GuestRegistration, Long>,
        JpaSpecificationExecutor<GuestRegistration> {

    // Số khách theo từng buổi (khách không có trạng thái hủy: có dòng là chiếm 1 chỗ)
    @Query("""
            select g.schedule.id as scheduleId, count(g) as total from GuestRegistration g
            where g.schedule.id in :scheduleIds
            group by g.schedule.id""")
    List<ScheduleCountView> countBySchedules(@Param("scheduleIds") Collection<Long> scheduleIds);

    boolean existsBySchedule_Id(Long scheduleId);

    // Cùng 1 SĐT không được có 2 lần trong 1 buổi (kể cả 2 hồ sơ khác nhau cùng SĐT)
    boolean existsBySchedule_IdAndGuest_Phone(Long scheduleId, String phone);

    // Sửa SĐT: không trùng khách khác (khác dòng đang sửa) trong cùng buổi
    boolean existsBySchedule_IdAndGuest_PhoneAndIdNot(Long scheduleId, String phone, Long id);

    // Hồ sơ khách có từng được người khác (hoặc không người dẫn) đưa vào buổi nào không — thành viên chỉ sửa
    // được hồ sơ chỉ gắn với khách của mình, tránh sửa thông tin khách của người khác
    @Query("""
            select count(g) > 0 from GuestRegistration g
            where g.guest.id = :guestId and (g.invitedBy is null or g.invitedBy.id <> :userId)""")
    boolean isGuestSharedWithOthers(@Param("guestId") Long guestId, @Param("userId") Long userId);

    // Hồ sơ khách (theo SĐT) mà userId từng dẫn — thành viên chỉ được dùng lại hồ sơ của khách mình dẫn
    @Query("""
            select g.guest from GuestRegistration g
            where g.guest.phone = :phone and g.invitedBy.id = :userId
            order by g.guest.id desc""")
    List<Guest> findGuestsInvitedBy(@Param("phone") String phone, @Param("userId") Long userId);

    // Số khách userId đang dẫn trong buổi (chặn hủy đăng ký khi còn khách)
    long countBySchedule_IdAndInvitedBy_Id(Long scheduleId, Long userId);

    // Khách của 1 buổi: nạp sẵn hồ sơ khách, người dẫn, người thu, buổi chơi
    @EntityGraph(attributePaths = {"guest", "invitedBy", "collectedBy", "schedule"})
    List<GuestRegistration> findBySchedule_IdOrderByRegisteredAtAscIdAsc(Long scheduleId);

    @EntityGraph(attributePaths = {"guest", "invitedBy", "collectedBy", "schedule"})
    Optional<GuestRegistration> findByIdAndSchedule_Id(Long id, Long scheduleId);

    // Trang "Phí khách": danh sách khách mọi buổi
    @Override
    @EntityGraph(attributePaths = {"guest", "invitedBy", "collectedBy", "schedule"})
    Page<GuestRegistration> findAll(Specification<GuestRegistration> spec, Pageable pageable);
}
