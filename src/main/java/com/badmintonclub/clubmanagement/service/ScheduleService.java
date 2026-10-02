package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.common.PageResponse;
import com.badmintonclub.clubmanagement.dto.schedule.ScheduleRequest;
import com.badmintonclub.clubmanagement.dto.schedule.ScheduleResponse;
import com.badmintonclub.clubmanagement.entity.Court;
import com.badmintonclub.clubmanagement.entity.Registration;
import com.badmintonclub.clubmanagement.entity.Schedule;
import com.badmintonclub.clubmanagement.enums.RegistrationStatus;
import com.badmintonclub.clubmanagement.enums.ScheduleStatus;
import com.badmintonclub.clubmanagement.enums.ScheduleType;
import com.badmintonclub.clubmanagement.exception.BusinessException;
import com.badmintonclub.clubmanagement.exception.ResourceNotFoundException;
import com.badmintonclub.clubmanagement.repository.AttendanceRepository;
import com.badmintonclub.clubmanagement.repository.CourtRepository;
import com.badmintonclub.clubmanagement.repository.ExpenseRepository;
import com.badmintonclub.clubmanagement.repository.GuestRegistrationRepository;
import com.badmintonclub.clubmanagement.repository.RegistrationRepository;
import com.badmintonclub.clubmanagement.repository.ScheduleRepository;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Buổi chơi tại 1 địa điểm (courts), thuê court_count sân. Tạo/sửa/đổi trạng thái/xóa: ADMIN.
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("playDate", "createdAt");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int NOTE_MAX_LENGTH = 255;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String LOCKED_WHEN_REGISTERED =
            "Buổi đã có người đăng ký nên không đổi được %s. Hãy hủy buổi này và tạo buổi mới.";

    private final ScheduleRepository scheduleRepository;
    private final CourtRepository courtRepository;
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final GuestRegistrationRepository guestRegistrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public PageResponse<ScheduleResponse> search(String keyword, LocalDate from, LocalDate to,
                                                 ScheduleStatus status, ScheduleType scheduleType, Long courtId,
                                                 boolean mine, Long currentUserId,
                                                 int page, int size, String sort, String direction) {
        Specification<Schedule> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                // Collation utf8mb4_unicode_ci: không phân biệt hoa thường và dấu
                String pattern = "%" + escapeLike(keyword.trim()) + "%";
                predicates.add(cb.or(
                        cb.like(root.get("title"), pattern, '\\'),
                        cb.like(root.get("court").get("courtName"), pattern, '\\')));
            }
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("playDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("playDate"), to));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (scheduleType != null) predicates.add(cb.equal(root.get("scheduleType"), scheduleType));
            if (courtId != null) predicates.add(cb.equal(root.get("court").get("id"), courtId));
            if (mine) {
                // Chỉ các buổi người đang xem đang đăng ký
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Registration> r = sub.from(Registration.class);
                sub.select(r.get("id")).where(
                        cb.equal(r.get("schedule"), root),
                        cb.equal(r.get("user").get("id"), currentUserId),
                        cb.equal(r.get("status"), RegistrationStatus.REGISTERED));
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortField = SORTABLE_FIELDS.contains(sort) ? sort : "playDate";
        // Cùng ngày thì xếp theo giờ bắt đầu
        Sort order = "playDate".equals(sortField)
                ? Sort.by(dir, "playDate").and(Sort.by(dir, "startTime")).and(Sort.by(dir, "id"))
                : Sort.by(dir, sortField).and(Sort.by(dir, "id"));
        Pageable pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), order);

        Page<Schedule> result = scheduleRepository.findAll(spec, pageable);
        List<Long> ids = result.getContent().stream().map(Schedule::getId).toList();
        Map<Long, Long> counts = countRegistered(ids);
        Set<Long> myScheduleIds = ids.isEmpty() ? Set.of() : Set.copyOf(
                registrationRepository.findScheduleIdsOfUser(currentUserId, ids, RegistrationStatus.REGISTERED));
        return PageResponse.from(result,
                s -> toResponse(s, counts.getOrDefault(s.getId(), 0L), myScheduleIds.contains(s.getId())));
    }

    @Transactional(readOnly = true)
    public ScheduleResponse getById(Long id, Long currentUserId) {
        return toResponse(findSchedule(id), currentUserId);
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest request, Long currentUserId) {
        Court court = findActiveCourt(request.courtId());
        validateTimeRange(request);
        validateNotStarted(request.playDate(), request.startTime());
        validateNoOverlap(court.getId(), request, 0L);

        Schedule schedule = new Schedule();
        schedule.setCourt(court);
        schedule.setCreatedBy(userRepository.getReferenceById(currentUserId));
        apply(schedule, request);
        return toResponse(scheduleRepository.save(schedule), 0, false);
    }

    @Transactional
    public ScheduleResponse update(Long id, ScheduleRequest request, Long currentUserId) {
        Schedule schedule = findSchedule(id);
        ensureEditable(schedule);
        validateTimeRange(request);

        boolean courtChanged = !schedule.getCourt().getId().equals(request.courtId());
        boolean timeChanged = !schedule.getPlayDate().equals(request.playDate())
                || !schedule.getStartTime().equals(request.startTime())
                || !schedule.getEndTime().equals(request.endTime());

        long registered = countRegistered(id);
        if (registered > 0) {
            // Chưa có thông báo cho người đã đăng ký -> không đổi ngầm địa điểm / thời gian
            if (courtChanged) {
                throw new BusinessException("courtId", LOCKED_WHEN_REGISTERED.formatted("địa điểm"));
            }
            if (!schedule.getPlayDate().equals(request.playDate())) {
                throw new BusinessException("playDate", LOCKED_WHEN_REGISTERED.formatted("ngày chơi"));
            }
            if (timeChanged) {
                throw new BusinessException("startTime", LOCKED_WHEN_REGISTERED.formatted("giờ chơi"));
            }
            if (request.maxPlayers() < registered) {
                throw new BusinessException("maxPlayers",
                        "Đã có " + registered + " người đăng ký, số người tối đa không được nhỏ hơn " + registered);
            }
        }

        // Sân tạm ngưng không được chọn mới; buổi đang ở sân đó (giữ nguyên sân) thì vẫn sửa được
        Court court = courtChanged ? findActiveCourt(request.courtId()) : schedule.getCourt();
        if (timeChanged) {
            validateNotStarted(request.playDate(), request.startTime());
        }
        if (courtChanged || timeChanged) {
            validateNoOverlap(court.getId(), request, id);
        }

        schedule.setCourt(court);
        apply(schedule, request);
        return toResponse(schedule, currentUserId);
    }

    // OPEN ⇄ CLOSED; OPEN/CLOSED → CANCELLED hoặc COMPLETED. Đã hủy / hoàn thành thì không đổi nữa.
    @Transactional
    public ScheduleResponse changeStatus(Long id, ScheduleStatus status, String reason, Long currentUserId) {
        Schedule schedule = findSchedule(id);
        ensureEditable(schedule);
        if (schedule.getStatus() == status) {
            throw new BusinessException("Buổi chơi đã ở trạng thái này");
        }
        switch (status) {
            case OPEN -> {
                if (isStarted(schedule)) {
                    throw new BusinessException("Buổi chơi đã qua giờ bắt đầu, không thể mở lại đăng ký");
                }
            }
            case COMPLETED -> {
                if (schedule.getPlayDate().isAfter(LocalDate.now())) {
                    throw new BusinessException("Chưa tới ngày chơi nên chưa thể đánh dấu hoàn thành");
                }
            }
            case CANCELLED -> {
                if (StringUtils.hasText(reason)) {
                    schedule.setNote(appendNote(schedule.getNote(), "Lý do hủy: " + reason.trim()));
                }
            }
            case CLOSED -> {
            }
        }
        schedule.setStatus(status);
        return toResponse(schedule, currentUserId);
    }

    // Chỉ xóa được buổi chưa có ai tham gia: không còn thành viên đang đăng ký, không có khách, điểm danh,
    // khoản chi. Đăng ký đã hủy không có giá trị lưu lại -> xóa cùng buổi. Còn lại thì hủy buổi.
    @Transactional
    public void delete(Long id) {
        Schedule schedule = findSchedule(id);
        String prefix = "Buổi \"" + schedule.getTitle() + "\" ";
        String suffix = " nên không thể xóa. Hãy chuyển sang hủy buổi.";
        long registered = registrationRepository.countBySchedule_IdAndStatus(id, RegistrationStatus.REGISTERED);
        if (registered > 0) {
            throw new BusinessException(prefix + "còn " + registered + " người đang đăng ký" + suffix);
        }
        if (guestRegistrationRepository.existsBySchedule_Id(id)) {
            throw new BusinessException(prefix + "đã có khách đăng ký" + suffix);
        }
        if (attendanceRepository.existsBySchedule_Id(id)) {
            throw new BusinessException(prefix + "đã có dữ liệu điểm danh" + suffix);
        }
        if (expenseRepository.existsBySchedule_Id(id)) {
            throw new BusinessException(prefix + "đã có khoản chi gắn với buổi" + suffix);
        }
        registrationRepository.deleteBySchedule(id);
        scheduleRepository.delete(schedule);
    }

    // Tiền thuê sân 1 buổi = giá 1 sân/giờ × số giờ × số sân (làm tròn đến đồng)
    public static BigDecimal calculateCourtRent(BigDecimal hourlyRate, LocalTime startTime, LocalTime endTime,
                                                int courtCount) {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return hourlyRate.multiply(BigDecimal.valueOf(minutes * courtCount))
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
    }

    private void apply(Schedule schedule, ScheduleRequest request) {
        schedule.setTitle(request.title().trim());
        schedule.setScheduleType(request.scheduleType());
        schedule.setCourtCount(request.courtCount());
        schedule.setPlayDate(request.playDate());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setMaxPlayers(request.maxPlayers());
        schedule.setNote(trimToNull(request.note()));
    }

    // Buổi chơi đã nạp court, createdBy (findWithCourtById) -> response cho người đang xem
    ScheduleResponse toResponse(Schedule schedule, Long currentUserId) {
        boolean mine = registrationRepository.findBySchedule_IdAndUser_Id(schedule.getId(), currentUserId)
                .map(r -> r.getStatus() == RegistrationStatus.REGISTERED)
                .orElse(false);
        return toResponse(schedule, countRegistered(schedule.getId()), mine);
    }

    private ScheduleResponse toResponse(Schedule schedule, long registeredCount, boolean registeredByMe) {
        BigDecimal cost = calculateCourtRent(schedule.getCourt().getHourlyRate(),
                schedule.getStartTime(), schedule.getEndTime(), schedule.getCourtCount());
        return ScheduleResponse.from(schedule, registeredCount, isStarted(schedule), registeredByMe,
                RegistrationService.selfCancelDeadline(schedule), cost);
    }

    private static void validateTimeRange(ScheduleRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException("endTime", "Giờ kết thúc phải sau giờ bắt đầu");
        }
    }

    private static void validateNotStarted(LocalDate playDate, LocalTime startTime) {
        if (playDate.isBefore(LocalDate.now())) {
            throw new BusinessException("playDate", "Ngày chơi không được ở quá khứ");
        }
        if (!LocalDateTime.of(playDate, startTime).isAfter(LocalDateTime.now())) {
            throw new BusinessException("startTime", "Giờ bắt đầu đã qua, vui lòng chọn giờ khác");
        }
    }

    // Không cho 2 buổi (chưa hủy) cùng địa điểm có khung giờ giao nhau
    private void validateNoOverlap(Long courtId, ScheduleRequest request, Long excludeId) {
        List<Schedule> overlaps = scheduleRepository.findOverlapping(courtId, request.playDate(),
                request.startTime(), request.endTime(), excludeId, ScheduleStatus.CANCELLED);
        if (!overlaps.isEmpty()) {
            Schedule other = overlaps.get(0);
            throw new BusinessException("startTime", "Trùng giờ với buổi \"" + other.getTitle() + "\" ("
                    + other.getStartTime().format(TIME_FORMAT) + "–" + other.getEndTime().format(TIME_FORMAT)
                    + ") tại địa điểm này");
        }
    }

    private static void ensureEditable(Schedule schedule) {
        if (schedule.getStatus() == ScheduleStatus.CANCELLED) {
            throw new BusinessException("Buổi chơi đã bị hủy, không thể thay đổi");
        }
        if (schedule.getStatus() == ScheduleStatus.COMPLETED) {
            throw new BusinessException("Buổi chơi đã hoàn thành, không thể thay đổi");
        }
    }

    static boolean isStarted(Schedule schedule) {
        return !LocalDateTime.of(schedule.getPlayDate(), schedule.getStartTime()).isAfter(LocalDateTime.now());
    }

    // Số chỗ đã dùng = thành viên đang đăng ký (REGISTERED) + khách
    private Map<Long, Long> countRegistered(Collection<Long> scheduleIds) {
        if (scheduleIds.isEmpty()) return Map.of();
        Map<Long, Long> counts = new HashMap<>();
        registrationRepository.countBySchedules(scheduleIds, RegistrationStatus.REGISTERED)
                .forEach(c -> counts.merge(c.getScheduleId(), c.getTotal(), Long::sum));
        guestRegistrationRepository.countBySchedules(scheduleIds)
                .forEach(c -> counts.merge(c.getScheduleId(), c.getTotal(), Long::sum));
        return counts;
    }

    long countRegistered(Long scheduleId) {
        return countRegistered(List.of(scheduleId)).getOrDefault(scheduleId, 0L);
    }

    Schedule findSchedule(Long id) {
        return scheduleRepository.findWithCourtById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi chơi"));
    }

    private Court findActiveCourt(Long courtId) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new BusinessException("courtId", "Sân không tồn tại"));
        if (!Boolean.TRUE.equals(court.getActive())) {
            throw new BusinessException("courtId", "Sân \"" + court.getCourtName() + "\" đang tạm ngưng, vui lòng chọn sân khác");
        }
        return court;
    }

    // Ghi thêm vào cuối ghi chú, cắt bớt nếu vượt độ dài cột
    private static String appendNote(String note, String extra) {
        String combined = StringUtils.hasText(note) ? note + " | " + extra : extra;
        return combined.length() > NOTE_MAX_LENGTH ? combined.substring(0, NOTE_MAX_LENGTH) : combined;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
