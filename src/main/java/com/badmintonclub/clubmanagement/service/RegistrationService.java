package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.registration.ParticipantResponse;
import com.badmintonclub.clubmanagement.dto.schedule.ScheduleResponse;
import com.badmintonclub.clubmanagement.entity.Attendance;
import com.badmintonclub.clubmanagement.entity.Registration;
import com.badmintonclub.clubmanagement.entity.Schedule;
import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.AttendanceStatus;
import com.badmintonclub.clubmanagement.enums.RegistrationStatus;
import com.badmintonclub.clubmanagement.enums.ScheduleStatus;
import com.badmintonclub.clubmanagement.enums.UserStatus;
import com.badmintonclub.clubmanagement.exception.BusinessException;
import com.badmintonclub.clubmanagement.exception.ResourceNotFoundException;
import com.badmintonclub.clubmanagement.repository.AttendanceRepository;
import com.badmintonclub.clubmanagement.repository.RegistrationRepository;
import com.badmintonclub.clubmanagement.repository.ScheduleRepository;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// Thành viên đăng ký / hủy đăng ký buổi chơi.
// Mọi lối vào (tự đăng ký, ADMIN thêm hộ) đi qua cùng register()/cancel() nên cùng quy tắc số chỗ, trạng thái, giờ.
@Service
@RequiredArgsConstructor
public class RegistrationService {

    // Thành viên tự hủy được đến trước giờ bắt đầu bao nhiêu tiếng; quá hạn thì nhờ ADMIN hủy hộ
    public static final int SELF_CANCEL_DEADLINE_HOURS = 3;
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final ScheduleService scheduleService;
    private final ScheduleRepository scheduleRepository;
    private final RegistrationRepository registrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    public static LocalDateTime selfCancelDeadline(Schedule schedule) {
        return LocalDateTime.of(schedule.getPlayDate(), schedule.getStartTime()).minusHours(SELF_CANCEL_DEADLINE_HOURS);
    }

    // Người tham gia (đang đăng ký) kèm kết quả điểm danh, theo thứ tự đăng ký
    @Transactional(readOnly = true)
    public List<ParticipantResponse> participants(Long scheduleId) {
        scheduleService.findSchedule(scheduleId);
        Map<Long, Attendance> attendances = attendanceRepository.findBySchedule_Id(scheduleId).stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), Function.identity()));
        return registrationRepository
                .findBySchedule_IdAndStatusOrderByRegisteredAtAscIdAsc(scheduleId, RegistrationStatus.REGISTERED)
                .stream()
                .map(r -> ParticipantResponse.from(r, attendances.get(r.getUser().getId())))
                .toList();
    }

    @Transactional
    public ScheduleResponse registerSelf(Long scheduleId, Long currentUserId) {
        register(scheduleId, currentUserId, true);
        return scheduleService.getById(scheduleId, currentUserId);
    }

    @Transactional
    public ScheduleResponse cancelSelf(Long scheduleId, Long currentUserId) {
        cancel(scheduleId, currentUserId, true);
        return scheduleService.getById(scheduleId, currentUserId);
    }

    // ADMIN thêm hộ: cùng quy tắc với tự đăng ký (buổi đang mở, chưa tới giờ, còn chỗ)
    @Transactional
    public ScheduleResponse addParticipant(Long scheduleId, Long userId, Long currentUserId) {
        register(scheduleId, userId, userId.equals(currentUserId));
        return scheduleService.getById(scheduleId, currentUserId);
    }

    // ADMIN hủy hộ: không bị hạn chót tự hủy. Đăng ký của chính mình phải tự hủy (cùng hạn chót như mọi người).
    @Transactional
    public ScheduleResponse removeParticipant(Long scheduleId, Long userId, Long currentUserId) {
        if (userId.equals(currentUserId)) {
            throw new BusinessException("Hủy đăng ký của chính bạn bằng nút \"Hủy đăng ký\" (áp dụng hạn chót "
                    + SELF_CANCEL_DEADLINE_HOURS + " tiếng như mọi thành viên)");
        }
        cancel(scheduleId, userId, false);
        return scheduleService.getById(scheduleId, currentUserId);
    }

    private void register(Long scheduleId, Long userId, boolean self) {
        // Khóa dòng buổi chơi: đếm số chỗ và ghi đăng ký trong cùng 1 lượt, không bị người khác chen giữa
        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi chơi"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("userId", "Thành viên không tồn tại"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("userId", "Thành viên \"" + user.getFullName()
                    + "\" đang bị khóa hoặc ngừng hoạt động, không thể đăng ký");
        }

        switch (schedule.getStatus()) {
            case CLOSED -> throw new BusinessException("Buổi chơi đã đóng đăng ký");
            case CANCELLED -> throw new BusinessException("Buổi chơi đã bị hủy");
            case COMPLETED -> throw new BusinessException("Buổi chơi đã kết thúc");
            case OPEN -> {
            }
        }
        if (ScheduleService.isStarted(schedule)) {
            throw new BusinessException("Buổi chơi đã bắt đầu, không thể đăng ký nữa");
        }

        Optional<Registration> existing = registrationRepository.findBySchedule_IdAndUser_Id(scheduleId, userId);
        if (existing.isPresent() && existing.get().getStatus() == RegistrationStatus.REGISTERED) {
            throw new BusinessException(self ? "Bạn đã đăng ký buổi này rồi"
                    : "\"" + user.getFullName() + "\" đã đăng ký buổi này rồi");
        }
        // Số chỗ đã dùng tính cả thành viên lẫn khách
        long registered = scheduleService.countRegistered(scheduleId);
        if (registered >= schedule.getMaxPlayers()) {
            throw new BusinessException("Buổi chơi đã đủ người (" + registered + "/" + schedule.getMaxPlayers() + ")");
        }

        // Hủy rồi đăng ký lại: dùng lại dòng cũ (unique user + buổi)
        Registration registration = existing.orElseGet(() -> {
            Registration r = new Registration();
            r.setUser(user);
            r.setSchedule(schedule);
            return r;
        });
        registration.setStatus(RegistrationStatus.REGISTERED);
        registration.setRegisteredAt(LocalDateTime.now());
        registrationRepository.save(registration);
    }

    private void cancel(Long scheduleId, Long userId, boolean self) {
        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi chơi"));
        if (schedule.getStatus() == ScheduleStatus.CANCELLED || schedule.getStatus() == ScheduleStatus.COMPLETED) {
            throw new BusinessException("Buổi chơi đã " + (schedule.getStatus() == ScheduleStatus.CANCELLED
                    ? "bị hủy" : "kết thúc") + ", không thể hủy đăng ký");
        }
        Registration registration = registrationRepository.findBySchedule_IdAndUser_Id(scheduleId, userId)
                .filter(r -> r.getStatus() == RegistrationStatus.REGISTERED)
                .orElseThrow(() -> new BusinessException(self ? "Bạn chưa đăng ký buổi này"
                        : "Thành viên này chưa đăng ký buổi này"));

        LocalDateTime deadline = selfCancelDeadline(schedule);
        if (self && !LocalDateTime.now().isBefore(deadline)) {
            throw new BusinessException("Đã quá hạn tự hủy (trước " + deadline.format(DEADLINE_FORMAT) + ", tức "
                    + SELF_CANCEL_DEADLINE_HOURS + " tiếng trước giờ chơi). Vui lòng liên hệ quản trị viên.");
        }

        // Đã điểm danh thì giữ nguyên để không mất kết quả; bỏ điểm danh trước rồi mới hủy được
        Optional<Attendance> attendance = attendanceRepository.findBySchedule_IdAndUser_Id(scheduleId, userId);
        if (attendance.isPresent() && attendance.get().getStatus() != AttendanceStatus.NOT_MARKED) {
            throw new BusinessException("Thành viên đã được điểm danh nên không thể hủy đăng ký. "
                    + "Hãy bỏ điểm danh trước nếu đánh dấu nhầm.");
        }
        attendance.ifPresent(attendanceRepository::delete);
        registration.setStatus(RegistrationStatus.CANCELLED);
    }
}
