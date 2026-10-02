package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.registration.ParticipantResponse;
import com.badmintonclub.clubmanagement.entity.Attendance;
import com.badmintonclub.clubmanagement.entity.Registration;
import com.badmintonclub.clubmanagement.entity.Schedule;
import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.AttendanceStatus;
import com.badmintonclub.clubmanagement.enums.RegistrationStatus;
import com.badmintonclub.clubmanagement.enums.ScheduleStatus;
import com.badmintonclub.clubmanagement.exception.BusinessException;
import com.badmintonclub.clubmanagement.repository.AttendanceRepository;
import com.badmintonclub.clubmanagement.repository.RegistrationRepository;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Điểm danh thành viên đã đăng ký (ADMIN). Điểm danh được từ ngày chơi trở đi, kể cả sau khi buổi đã hoàn thành
// (để sửa sai); buổi đã hủy thì không.
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final ScheduleService scheduleService;
    private final RegistrationService registrationService;
    private final RegistrationRepository registrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    @Transactional
    public ParticipantResponse mark(Long scheduleId, Long userId, AttendanceStatus status, String note,
                                    Long currentUserId) {
        Schedule schedule = scheduleService.findSchedule(scheduleId);
        ensureCanMark(schedule);
        Registration registration = registrationRepository.findBySchedule_IdAndUser_Id(scheduleId, userId)
                .filter(r -> r.getStatus() == RegistrationStatus.REGISTERED)
                .orElseThrow(() -> new BusinessException("Thành viên này không có trong danh sách đăng ký của buổi"));

        Attendance attendance = attendanceRepository.findBySchedule_IdAndUser_Id(scheduleId, userId)
                .orElseGet(() -> newAttendance(schedule, registration.getUser()));
        // Không gửi note (vd chỉ bấm đổi trạng thái) -> giữ ghi chú cũ; gửi chuỗi rỗng -> xóa ghi chú
        String newNote = note == null ? attendance.getNote() : (StringUtils.hasText(note) ? note.trim() : null);
        apply(attendance, status, newNote, currentUserId);
        return ParticipantResponse.from(registration, attendanceRepository.save(attendance));
    }

    // Đánh dấu nhanh tất cả người CHƯA điểm danh (người đã điểm danh giữ nguyên)
    @Transactional
    public List<ParticipantResponse> markAll(Long scheduleId, AttendanceStatus status, Long currentUserId) {
        if (status == AttendanceStatus.NOT_MARKED) {
            throw new BusinessException("status", "Chỉ đánh dấu nhanh được \"Có mặt\" hoặc \"Vắng\"");
        }
        Schedule schedule = scheduleService.findSchedule(scheduleId);
        ensureCanMark(schedule);
        Map<Long, Attendance> attendances = attendanceRepository.findBySchedule_Id(scheduleId).stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), Function.identity()));
        registrationRepository
                .findBySchedule_IdAndStatusOrderByRegisteredAtAscIdAsc(scheduleId, RegistrationStatus.REGISTERED)
                .forEach(r -> {
                    Attendance attendance = attendances.get(r.getUser().getId());
                    if (attendance != null && attendance.getStatus() != AttendanceStatus.NOT_MARKED) return;
                    if (attendance == null) attendance = newAttendance(schedule, r.getUser());
                    apply(attendance, status, attendance.getNote(), currentUserId);
                    attendanceRepository.save(attendance);
                });
        return registrationService.participants(scheduleId);
    }

    private void ensureCanMark(Schedule schedule) {
        if (schedule.getStatus() == ScheduleStatus.CANCELLED) {
            throw new BusinessException("Buổi chơi đã bị hủy, không thể điểm danh");
        }
        if (schedule.getPlayDate().isAfter(LocalDate.now())) {
            throw new BusinessException("Chưa tới ngày chơi, chưa thể điểm danh");
        }
    }

    // NOT_MARKED = bỏ điểm danh: xóa người/thời điểm điểm danh
    private void apply(Attendance attendance, AttendanceStatus status, String note, Long currentUserId) {
        boolean marked = status != AttendanceStatus.NOT_MARKED;
        attendance.setStatus(status);
        attendance.setCheckedAt(marked ? LocalDateTime.now() : null);
        attendance.setCheckedBy(marked ? userRepository.getReferenceById(currentUserId) : null);
        attendance.setNote(note);
    }

    private static Attendance newAttendance(Schedule schedule, User user) {
        Attendance attendance = new Attendance();
        attendance.setSchedule(schedule);
        attendance.setUser(user);
        return attendance;
    }
}
