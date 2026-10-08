package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.common.PageResponse;
import com.badmintonclub.clubmanagement.dto.guest.GuestFeeListResponse;
import com.badmintonclub.clubmanagement.dto.guest.GuestRegistrationResponse;
import com.badmintonclub.clubmanagement.dto.guest.GuestRequest;
import com.badmintonclub.clubmanagement.entity.FeeSetting;
import com.badmintonclub.clubmanagement.entity.Guest;
import com.badmintonclub.clubmanagement.entity.GuestRegistration;
import com.badmintonclub.clubmanagement.entity.Schedule;
import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.AttendanceStatus;
import com.badmintonclub.clubmanagement.enums.FeeType;
import com.badmintonclub.clubmanagement.enums.PaymentStatus;
import com.badmintonclub.clubmanagement.enums.ScheduleStatus;
import com.badmintonclub.clubmanagement.exception.BusinessException;
import com.badmintonclub.clubmanagement.exception.ResourceNotFoundException;
import com.badmintonclub.clubmanagement.repository.GuestRegistrationRepository;
import com.badmintonclub.clubmanagement.repository.GuestRepository;
import com.badmintonclub.clubmanagement.repository.ScheduleRepository;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// Khách vãng lai theo buổi chơi.
// - Thêm khách: cùng quy tắc với thành viên đăng ký (RegistrationService.ensureJoinable / ensureHasSlot, khóa dòng buổi).
//   Thành viên chỉ dẫn khách vào buổi mình đang đăng ký (người dẫn = chính mình); ADMIN/TREASURER thêm hộ.
// - Phí khách chép từ mức GUEST áp dụng tại ngày chơi (FeeSettingService.findEffective) lúc thêm.
// - Hủy (xóa dòng): người dẫn tự hủy trước hạn chót như thành viên; ADMIN/TREASURER hủy hộ khách của người khác.
// - Thu tiền: ADMIN/TREASURER. "manager" = người đang đăng nhập có vai trò ADMIN hoặc TREASURER.
@Service
@RequiredArgsConstructor
public class GuestService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("playDate", "registeredAt");
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final ScheduleService scheduleService;
    private final RegistrationService registrationService;
    private final FeeSettingService feeSettingService;
    private final ScheduleRepository scheduleRepository;
    private final GuestRepository guestRepository;
    private final GuestRegistrationRepository guestRegistrationRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    // Khách của 1 buổi; SĐT/phí/thu tiền chỉ hiện với người quản lý và người dẫn khách đó
    @Transactional(readOnly = true)
    public List<GuestRegistrationResponse> list(Long scheduleId, Long currentUserId, boolean manager) {
        scheduleService.findSchedule(scheduleId);
        return guestRegistrationRepository.findBySchedule_IdOrderByRegisteredAtAscIdAsc(scheduleId).stream()
                .map(gr -> GuestRegistrationResponse.from(gr, manager || isInviter(gr, currentUserId)))
                .toList();
    }

    @Transactional
    public GuestRegistrationResponse add(Long scheduleId, GuestRequest request, Long currentUserId, boolean manager) {
        // Khóa dòng buổi chơi: đếm chỗ và thêm khách trong cùng 1 lượt (giống thành viên đăng ký)
        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi chơi"));
        RegistrationService.ensureJoinable(schedule);
        User inviter = resolveInviter(scheduleId, request.invitedById(), currentUserId, manager);
        registrationService.ensureHasSlot(schedule);

        FeeSetting fee = feeSettingService.findEffective(FeeType.GUEST, null, schedule.getPlayDate())
                .orElseThrow(() -> new BusinessException("Chưa có mức phí khách áp dụng cho ngày "
                        + schedule.getPlayDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + ". Quản trị viên cần cài đặt ở mục Mức phí."));

        String phone = trimToNull(request.phone());
        // Không nêu tên khách trong thông báo: người gọi có thể không được xem hồ sơ đó
        if (phone != null && guestRegistrationRepository.existsBySchedule_IdAndGuest_Phone(scheduleId, phone)) {
            throw new BusinessException("phone", "Khách có số điện thoại " + phone + " đã có trong buổi chơi này");
        }
        Guest guest = phone == null ? null : findReusableGuest(phone, currentUserId, manager);
        if (guest == null) {
            guest = new Guest();
            guest.setFullName(request.fullName().trim());
            guest.setPhone(phone);
            guest.setGender(request.gender());
            guest.setNote(trimToNull(request.note()));
            guest = guestRepository.save(guest);
        }

        GuestRegistration gr = new GuestRegistration();
        gr.setGuest(guest);
        gr.setSchedule(schedule);
        gr.setInvitedBy(inviter);
        gr.setFee(fee.getAmount());
        return GuestRegistrationResponse.from(guestRegistrationRepository.save(gr), true);
    }

    // Sửa thông tin khách (tên, SĐT, giới tính, ghi chú) và người dẫn. Không sửa phí (lấy từ Mức phí).
    // - ADMIN/TREASURER: sửa bất kỳ lúc nào (kể cả khách đã thu tiền / đã điểm danh), đổi được người dẫn.
    // - Thành viên: chỉ khách do mình dẫn, buổi chưa hủy/kết thúc, và hồ sơ chưa từng gắn với người khác
    //   (hồ sơ guests dùng chung giữa các buổi: sửa ở đây đổi luôn tên ở các buổi khác).
    @Transactional
    public GuestRegistrationResponse update(Long scheduleId, Long guestRegistrationId, GuestRequest request,
                                            Long currentUserId, boolean manager) {
        GuestRegistration gr = findGuestRegistration(scheduleId, guestRegistrationId);
        Schedule schedule = gr.getSchedule();
        Guest guest = gr.getGuest();
        if (!manager) {
            if (!isInviter(gr, currentUserId)) {
                throw new AccessDeniedException("Bạn chỉ sửa được khách do mình dẫn");
            }
            if (request.invitedById() != null && !request.invitedById().equals(currentUserId)) {
                throw new AccessDeniedException("Bạn không thể đổi người dẫn khách");
            }
            if (schedule.getStatus() == ScheduleStatus.CANCELLED || schedule.getStatus() == ScheduleStatus.COMPLETED) {
                throw new BusinessException("Buổi chơi đã " + (schedule.getStatus() == ScheduleStatus.CANCELLED
                        ? "bị hủy" : "kết thúc") + ". Liên hệ quản trị viên hoặc thủ quỹ nếu cần sửa thông tin khách.");
            }
            if (guestRegistrationRepository.isGuestSharedWithOthers(guest.getId(), currentUserId)) {
                throw new BusinessException("Thông tin khách này đang dùng chung với khách của người khác. "
                        + "Liên hệ quản trị viên hoặc thủ quỹ để sửa.");
            }
        }

        String phone = trimToNull(request.phone());
        if (phone != null && guestRegistrationRepository
                .existsBySchedule_IdAndGuest_PhoneAndIdNot(scheduleId, phone, guestRegistrationId)) {
            throw new BusinessException("phone", "Khách có số điện thoại " + phone + " đã có trong buổi chơi này");
        }
        // Đổi người dẫn (chỉ ADMIN/TREASURER): người mới phải đang đăng ký buổi, cùng quy tắc khi thêm khách
        if (manager && !Objects.equals(request.invitedById(),
                gr.getInvitedBy() != null ? gr.getInvitedBy().getId() : null)) {
            gr.setInvitedBy(resolveInviter(scheduleId, request.invitedById(), currentUserId, true));
        }

        guest.setFullName(request.fullName().trim());
        guest.setPhone(phone);
        guest.setGender(request.gender());
        guest.setNote(trimToNull(request.note()));
        return GuestRegistrationResponse.from(gr, true);
    }

    @Transactional
    public void remove(Long scheduleId, Long guestRegistrationId, Long currentUserId, boolean manager) {
        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi chơi"));
        GuestRegistration gr = findGuestRegistration(scheduleId, guestRegistrationId);
        boolean inviter = isInviter(gr, currentUserId);
        if (!manager && !inviter) {
            throw new AccessDeniedException("Bạn chỉ hủy được khách do mình dẫn");
        }
        if (schedule.getStatus() == ScheduleStatus.CANCELLED || schedule.getStatus() == ScheduleStatus.COMPLETED) {
            throw new BusinessException("Buổi chơi đã " + (schedule.getStatus() == ScheduleStatus.CANCELLED
                    ? "bị hủy" : "kết thúc") + ", không thể hủy khách");
        }
        // Khách do chính mình dẫn: cùng hạn chót tự hủy như đăng ký của thành viên (kể cả ADMIN/TREASURER)
        LocalDateTime deadline = RegistrationService.selfCancelDeadline(schedule);
        if (inviter && !LocalDateTime.now().isBefore(deadline)) {
            throw new BusinessException("Đã quá hạn tự hủy khách (trước " + deadline.format(DEADLINE_FORMAT)
                    + "). Vui lòng liên hệ quản trị viên hoặc thủ quỹ.");
        }
        if (gr.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException("Khách \"" + gr.getGuest().getFullName()
                    + "\" đã trả tiền nên không thể hủy. Hãy chuyển về \"Chưa thu\" trước nếu ghi nhầm.");
        }
        if (gr.getAttendanceStatus() != AttendanceStatus.NOT_MARKED) {
            throw new BusinessException("Khách \"" + gr.getGuest().getFullName()
                    + "\" đã được điểm danh nên không thể hủy. Hãy bỏ điểm danh trước nếu đánh dấu nhầm.");
        }
        guestRegistrationRepository.delete(gr);
    }

    // Đã thu: ghi thời điểm và người thu; chưa thu: xóa 2 thông tin đó
    @Transactional
    public GuestRegistrationResponse setPayment(Long scheduleId, Long guestRegistrationId, PaymentStatus status,
                                                Long currentUserId) {
        GuestRegistration gr = findGuestRegistration(scheduleId, guestRegistrationId);
        if (gr.getPaymentStatus() == status) {
            throw new BusinessException(status == PaymentStatus.PAID
                    ? "Khách này đã được ghi nhận đã thu tiền" : "Khách này đang ở trạng thái chưa thu");
        }
        if (status == PaymentStatus.PAID && gr.getSchedule().getStatus() == ScheduleStatus.CANCELLED) {
            throw new BusinessException("Buổi chơi đã bị hủy, không thu phí khách");
        }
        boolean paid = status == PaymentStatus.PAID;
        gr.setPaymentStatus(status);
        gr.setPaidAt(paid ? LocalDateTime.now() : null);
        gr.setCollectedBy(paid ? userRepository.getReferenceById(currentUserId) : null);
        return GuestRegistrationResponse.from(gr, true);
    }

    // Trang "Phí khách" (ADMIN/TREASURER): khách mọi buổi theo bộ lọc + tổng đã thu / chưa thu.
    // Tổng không phụ thuộc bộ lọc trạng thái thu để luôn thấy cả 2 con số.
    @Transactional(readOnly = true)
    public GuestFeeListResponse searchFees(String keyword, LocalDate from, LocalDate to, PaymentStatus paymentStatus,
                                           boolean includeCancelled, int page, int size, String sort, String direction) {
        Specification<GuestRegistration> base = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + escapeLike(keyword.trim()) + "%";
                predicates.add(cb.or(
                        cb.like(root.get("guest").get("fullName"), pattern, '\\'),
                        cb.like(root.get("guest").get("phone"), pattern, '\\'),
                        cb.like(root.join("invitedBy", JoinType.LEFT).get("fullName"), pattern, '\\'),
                        cb.like(root.get("schedule").get("title"), pattern, '\\')));
            }
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("schedule").get("playDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("schedule").get("playDate"), to));
            if (!includeCancelled) {
                predicates.add(cb.notEqual(root.get("schedule").get("status"), ScheduleStatus.CANCELLED));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Specification<GuestRegistration> listSpec = paymentStatus == null ? base
                : base.and((root, query, cb) -> cb.equal(root.get("paymentStatus"), paymentStatus));

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = SORTABLE_FIELDS.contains(sort) ? sort : "playDate";
        Sort order = "playDate".equals(sortField)
                ? Sort.by(dir, "schedule.playDate").and(Sort.by(dir, "schedule.startTime")).and(Sort.by(dir, "id"))
                : Sort.by(dir, "registeredAt").and(Sort.by(dir, "id"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), order);

        PageResponse<GuestRegistrationResponse> result = PageResponse.from(
                guestRegistrationRepository.findAll(listSpec, pageable), gr -> GuestRegistrationResponse.from(gr, true));

        long paidCount = 0, unpaidCount = 0;
        BigDecimal paidAmount = BigDecimal.ZERO, unpaidAmount = BigDecimal.ZERO;
        for (Tuple t : totalsByPaymentStatus(base)) {
            long count = t.get("count", Long.class);
            BigDecimal amount = t.get("amount", BigDecimal.class);
            amount = amount != null ? amount : BigDecimal.ZERO;
            if (t.get("status", PaymentStatus.class) == PaymentStatus.PAID) {
                paidCount = count;
                paidAmount = amount;
            } else {
                unpaidCount += count;
                unpaidAmount = unpaidAmount.add(amount);
            }
        }
        return new GuestFeeListResponse(result, paidCount, paidAmount, unpaidCount, unpaidAmount);
    }

    // Đếm và cộng phí theo trạng thái thu, cùng điều kiện lọc với danh sách
    private List<Tuple> totalsByPaymentStatus(Specification<GuestRegistration> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<GuestRegistration> root = query.from(GuestRegistration.class);
        Predicate where = spec.toPredicate(root, query, cb);
        query.select(cb.tuple(
                root.get("paymentStatus").alias("status"),
                cb.count(root).alias("count"),
                cb.sum(root.<BigDecimal>get("fee")).alias("amount")));
        if (where != null) query.where(where);
        query.groupBy(root.get("paymentStatus"));
        return entityManager.createQuery(query).getResultList();
    }

    // Khách quay lại (cùng SĐT) dùng lại hồ sơ cũ. ADMIN/TREASURER vốn xem được mọi hồ sơ nên dùng lại hồ sơ bất kỳ;
    // thành viên chỉ dùng lại hồ sơ khách do chính mình từng dẫn — nếu không, nhập 1 SĐT bất kỳ sẽ nhận về tên,
    // giới tính, ghi chú của khách người khác (dò thông tin qua SĐT). Không có thì tạo hồ sơ mới theo tên vừa nhập.
    private Guest findReusableGuest(String phone, Long currentUserId, boolean manager) {
        if (manager) {
            return guestRepository.findFirstByPhoneOrderByIdDesc(phone).orElse(null);
        }
        return guestRegistrationRepository.findGuestsInvitedBy(phone, currentUserId).stream().findFirst().orElse(null);
    }

    // Người dẫn khách: thành viên thường luôn là chính mình; ADMIN/TREASURER chọn người dẫn hoặc để trống.
    // Người dẫn (nếu có) phải đang đăng ký buổi đó — áp dụng như nhau cho mọi vai trò.
    private User resolveInviter(Long scheduleId, Long requestedInviterId, Long currentUserId, boolean manager) {
        Long inviterId = manager ? requestedInviterId : currentUserId;
        if (!manager && requestedInviterId != null && !requestedInviterId.equals(currentUserId)) {
            throw new AccessDeniedException("Bạn chỉ dẫn khách với tư cách người dẫn là chính mình");
        }
        if (inviterId == null) return null;
        User inviter = userRepository.findById(inviterId)
                .orElseThrow(() -> new BusinessException("invitedById", "Người dẫn khách không tồn tại"));
        if (!registrationService.isRegistered(scheduleId, inviterId)) {
            throw new BusinessException("invitedById", inviterId.equals(currentUserId)
                    ? "Bạn cần đăng ký tham gia buổi này trước khi dẫn khách"
                    : "\"" + inviter.getFullName() + "\" chưa đăng ký buổi này nên không thể là người dẫn khách");
        }
        return inviter;
    }

    GuestRegistration findGuestRegistration(Long scheduleId, Long guestRegistrationId) {
        return guestRegistrationRepository.findByIdAndSchedule_Id(guestRegistrationId, scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách trong buổi chơi này"));
    }

    private static boolean isInviter(GuestRegistration gr, Long userId) {
        return gr.getInvitedBy() != null && gr.getInvitedBy().getId().equals(userId);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
