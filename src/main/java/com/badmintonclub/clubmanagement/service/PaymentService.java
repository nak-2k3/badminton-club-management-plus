package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.common.OptionResponse;
import com.badmintonclub.clubmanagement.dto.common.PageResponse;
import com.badmintonclub.clubmanagement.dto.payment.BatchDeleteResponse;
import com.badmintonclub.clubmanagement.dto.payment.BulkResultResponse;
import com.badmintonclub.clubmanagement.dto.payment.MonthlyGenerateResponse;
import com.badmintonclub.clubmanagement.dto.payment.PaymentBatchListResponse;
import com.badmintonclub.clubmanagement.dto.payment.PaymentBatchResponse;
import com.badmintonclub.clubmanagement.dto.payment.PaymentBatchUpdateRequest;
import com.badmintonclub.clubmanagement.dto.payment.PaymentCreateRequest;
import com.badmintonclub.clubmanagement.dto.payment.PaymentListResponse;
import com.badmintonclub.clubmanagement.dto.payment.PaymentResponse;
import com.badmintonclub.clubmanagement.dto.payment.PaymentUpdateRequest;
import com.badmintonclub.clubmanagement.entity.FeeSetting;
import com.badmintonclub.clubmanagement.entity.Payment;
import com.badmintonclub.clubmanagement.entity.PaymentBatch;
import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.FeeType;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.badmintonclub.clubmanagement.enums.PaymentMethod;
import com.badmintonclub.clubmanagement.enums.PaymentStatus;
import com.badmintonclub.clubmanagement.enums.PaymentType;
import com.badmintonclub.clubmanagement.enums.UserStatus;
import com.badmintonclub.clubmanagement.exception.BusinessException;
import com.badmintonclub.clubmanagement.exception.ResourceNotFoundException;
import com.badmintonclub.clubmanagement.repository.BatchStatsView;
import com.badmintonclub.clubmanagement.repository.PaymentBatchRepository;
import com.badmintonclub.clubmanagement.repository.PaymentRepository;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Khoản thu của thành viên (ADMIN, TREASURER), gom theo đợt thu (payment_batches):
 * phí tháng mỗi tháng 1 đợt, thu thêm mỗi lần tạo 1 đợt.
 * - Thêm người (tạo mới, thêm vào đợt, tạo phí tháng cho tất cả) đều đi qua buildPayments():
 *   chỉ thành viên ACTIVE, không trùng, phí tháng lấy theo FeeSettingService.findEffective(MONTHLY, giới tính, ngày 1 của tháng).
 * - Xóa (1 khoản, nhiều khoản, cả đợt) đều đi qua deleteUnpaid(): chỉ xóa khoản chưa thu, đợt hết người thì xóa đợt.
 * - Thu tiền (1 khoản, nhiều khoản) đều đi qua collect(): PAID bắt buộc hình thức, ghi paid_at.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("period", "createdAt", "amount", "paidAt");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int OPTION_LIMIT = 500;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<Gender, String> GENDER_NAMES = Map.of(Gender.MALE, "Nam", Gender.FEMALE, "Nữ");

    private final PaymentRepository paymentRepository;
    private final PaymentBatchRepository batchRepository;
    private final UserRepository userRepository;
    private final FeeSettingService feeSettingService;
    private final EntityManager entityManager;

    // ---------------------------------------------------------------- Đợt thu

    // Danh sách đợt thu (mới nhất trước) + tổng đã thu / chưa thu của mọi khoản trong các đợt khớp bộ lọc
    @Transactional(readOnly = true)
    public PaymentBatchListResponse searchBatches(String keyword, PaymentType paymentType, Integer month, Integer year,
                                                  int page, int size) {
        Specification<PaymentBatch> spec = (root, query, cb) -> batchFilter(root, cb, keyword, paymentType, month, year);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<PaymentBatch> batches = batchRepository.findAll(spec, pageable);
        Map<Long, BatchStatsView> stats = statsOf(batches.getContent().stream().map(PaymentBatch::getId).toList());

        Totals totals = totals((root, query, cb) ->
                batchFilter(root.get("batch"), cb, keyword, paymentType, month, year));
        return new PaymentBatchListResponse(
                PageResponse.from(batches, b -> PaymentBatchResponse.from(b, stats.get(b.getId()))),
                totals.paidCount, totals.paidAmount, totals.unpaidCount, totals.unpaidAmount);
    }

    @Transactional(readOnly = true)
    public PaymentBatchResponse getBatch(Long id) {
        return toResponse(findBatch(id));
    }

    // Người trong đợt (theo tên) — đợt thu chỉ vài chục người nên trả hết, không phân trang
    @Transactional(readOnly = true)
    public List<PaymentResponse> batchPayments(Long id) {
        findBatch(id);
        return paymentRepository.findByBatch_IdOrderByUser_FullNameAscIdAsc(id).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    // Sửa đợt: nội dung, ghi chú; số tiền (chỉ thu thêm) áp dụng cho người chưa nộp, người đã nộp giữ nguyên
    @Transactional
    public PaymentBatchResponse updateBatch(Long id, PaymentBatchUpdateRequest request) {
        PaymentBatch batch = findBatch(id);
        if (batch.getPaymentType() == PaymentType.MONTHLY) {
            if (request.amount() != null) {
                throw new BusinessException("amount", "Số tiền phí tháng lấy theo Mức phí, không sửa tay");
            }
        } else {
            if (request.amount() == null) throw new BusinessException("amount", "Vui lòng nhập số tiền");
            batch.setAmount(request.amount());
            for (Payment payment : paymentRepository.findByBatch_IdOrderByUser_FullNameAscIdAsc(id)) {
                if (payment.getStatus() == PaymentStatus.UNPAID) payment.setAmount(request.amount());
            }
        }
        batch.setDescription(request.description().trim());
        batch.setNote(trimToNull(request.note()));
        return toResponse(batch);
    }

    // Thêm người vào đợt (quên ai đó lúc tạo): cùng quy tắc với lúc tạo
    @Transactional
    public PaymentBatchResponse addBatchMembers(Long id, List<Long> userIds, Long currentUserId) {
        PaymentBatch batch = findBatch(id);
        List<User> users = findActiveUsers(userIds);
        paymentRepository.saveAll(buildPayments(batch, users, userRepository.getReferenceById(currentUserId)));
        return toResponse(batch);
    }

    // Xóa đợt: xóa mọi khoản chưa thu; còn người đã nộp thì giữ họ và giữ đợt (giao diện báo trước khi xóa)
    @Transactional
    public BatchDeleteResponse deleteBatch(Long id) {
        findBatch(id);
        List<Payment> payments = paymentRepository.findByBatch_IdOrderByUser_FullNameAscIdAsc(id);
        int deleted = deleteUnpaid(payments);
        int kept = payments.size() - deleted;
        return new BatchDeleteResponse(deleted, kept, kept == 0);
    }

    // ---------------------------------------------------------------- Tạo khoản thu

    // Thu thêm: tạo 1 đợt mới cho những người được chọn. Phí tháng: thêm vào đợt của tháng đó (chưa có thì tạo).
    // Có người không hợp lệ thì báo tên và không tạo gì (cả transaction rollback).
    @Transactional
    public PaymentBatchResponse create(PaymentCreateRequest request, Long currentUserId) {
        List<User> users = findActiveUsers(request.userIds());
        User creator = userRepository.getReferenceById(currentUserId);

        PaymentBatch batch;
        if (request.paymentType() == PaymentType.MONTHLY) {
            if (request.month() == null) throw new BusinessException("month", "Vui lòng chọn tháng");
            if (request.year() == null) throw new BusinessException("year", "Vui lòng chọn năm");
            batch = monthlyBatch(request.month(), request.year(), creator);
        } else {
            String description = trimToNull(request.description());
            if (description == null) throw new BusinessException("description", "Vui lòng nhập nội dung khoản thu");
            if (request.amount() == null) throw new BusinessException("amount", "Vui lòng nhập số tiền");
            batch = new PaymentBatch();
            batch.setPaymentType(PaymentType.EXTRA);
            batch.setDescription(description);
            batch.setAmount(request.amount());
            batch.setNote(trimToNull(request.note()));
            batch.setCreatedBy(creator);
            batch = batchRepository.save(batch);
        }
        List<Payment> payments = buildPayments(batch, users, creator);
        if (batch.getPaymentType() == PaymentType.MONTHLY) {
            String note = trimToNull(request.note());
            payments.forEach(p -> p.setNote(note));
        }
        paymentRepository.saveAll(payments);
        return toResponse(batch);
    }

    // Tạo phí tháng cho mọi thành viên đang hoạt động; người đã có phí tháng này thì bỏ qua, mức phí 0 ₫ thì miễn.
    // Thiếu mức phí cho giới tính nào thì báo lỗi và không tạo gì.
    @Transactional
    public MonthlyGenerateResponse generateMonthly(int month, int year, Long currentUserId) {
        Set<Long> existing = paymentRepository.findUserIdsByPeriod(PaymentType.MONTHLY, year, month);
        List<User> active = userRepository.findByStatus(UserStatus.ACTIVE);
        List<User> targets = active.stream().filter(u -> !existing.contains(u.getId())).toList();

        Map<Gender, BigDecimal> fees = new EnumMap<>(Gender.class);
        for (User user : targets) {
            fees.computeIfAbsent(user.getGender(), g -> monthlyFee(g, month, year));
        }
        List<User> payers = targets.stream().filter(u -> fees.get(u.getGender()).signum() > 0).toList();

        User creator = userRepository.getReferenceById(currentUserId);
        PaymentBatch batch = payers.isEmpty()
                ? batchRepository.findByPaymentTypeAndYearAndMonth(PaymentType.MONTHLY, year, month).orElse(null)
                : monthlyBatch(month, year, creator);
        BigDecimal total = BigDecimal.ZERO;
        for (User user : payers) {
            BigDecimal amount = fees.get(user.getGender());
            paymentRepository.save(newPayment(batch, user, amount, creator));
            total = total.add(amount);
        }
        return new MonthlyGenerateResponse(batch != null ? batch.getId() : null, month, year, payers.size(),
                active.size() - targets.size(), targets.size() - payers.size(), total);
    }

    // ---------------------------------------------------------------- Từng khoản / nhiều khoản

    // Tab "Tất cả khoản": danh sách + tổng đã thu / chưa thu (tổng không phụ thuộc bộ lọc trạng thái thu)
    @Transactional(readOnly = true)
    public PaymentListResponse search(String keyword, PaymentType paymentType, PaymentStatus status,
                                      Integer month, Integer year, int page, int size, String sort, String direction) {
        Specification<Payment> base = (root, query, cb) -> {
            Path<PaymentBatch> batch = root.get("batch");
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + escapeLike(keyword.trim()) + "%";
                predicates.add(cb.or(
                        cb.like(root.get("user").get("fullName"), pattern, '\\'),
                        cb.like(root.get("user").get("email"), pattern, '\\'),
                        cb.like(root.get("user").get("phone"), pattern, '\\'),
                        cb.like(batch.get("description"), pattern, '\\')));
            }
            predicates.add(batchFilter(batch, cb, null, paymentType, month, year));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Specification<Payment> listSpec = status == null ? base
                : base.and((root, query, cb) -> cb.equal(root.get("status"), status));

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = SORTABLE_FIELDS.contains(sort) ? sort : "period";
        Sort order = "period".equals(sortField)
                ? Sort.by(dir, "year").and(Sort.by(dir, "month")).and(Sort.by(Sort.Direction.DESC, "createdAt"))
                : Sort.by(dir, sortField);
        order = order.and(Sort.by(Sort.Direction.ASC, "user.fullName")).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), order);

        PageResponse<PaymentResponse> result = PageResponse.from(
                paymentRepository.findAll(listSpec, pageable), PaymentResponse::from);
        Totals totals = totals(base);
        return new PaymentListResponse(result, totals.paidCount, totals.paidAmount, totals.unpaidCount, totals.unpaidAmount);
    }

    // "Khoản phí của tôi" (mọi vai trò, chỉ xem)
    @Transactional(readOnly = true)
    public List<PaymentResponse> myPayments(Long currentUserId) {
        return paymentRepository.findByUser_IdOrderByYearDescMonthDescCreatedAtDescIdDesc(currentUserId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    // Danh sách chọn thành viên (TREASURER không xem được /api/members): mọi người đang hoạt động,
    // giao diện tự lọc + "Chọn tất cả". CLB nhỏ nên trả hết (giới hạn OPTION_LIMIT).
    @Transactional(readOnly = true)
    public List<OptionResponse> memberOptions() {
        Specification<User> spec = (root, query, cb) -> cb.equal(root.get("status"), UserStatus.ACTIVE);
        Pageable pageable = PageRequest.of(0, OPTION_LIMIT, Sort.by("fullName").and(Sort.by("id")));
        return userRepository.findAll(spec, pageable).stream().map(OptionResponse::from).toList();
    }

    // Sửa 1 khoản chưa thu: ghi chú; số tiền chỉ với thu thêm (phí tháng lấy theo mức phí). Nội dung sửa ở đợt.
    @Transactional
    public PaymentResponse update(Long id, PaymentUpdateRequest request) {
        Payment payment = findPayment(id);
        ensureUnpaid(payment, "sửa");
        if (request.amount() != null && request.amount().compareTo(payment.getAmount()) != 0) {
            if (payment.getPaymentType() == PaymentType.MONTHLY) {
                throw new BusinessException("amount",
                        "Số tiền phí tháng lấy theo Mức phí, không sửa tay. Muốn đổi thì xóa khoản này và tạo lại");
            }
            payment.setAmount(request.amount());
        }
        payment.setNote(trimToNull(request.note()));
        return PaymentResponse.from(payment);
    }

    @Transactional
    public void delete(Long id) {
        Payment payment = findPayment(id);
        ensureUnpaid(payment, "xóa");
        deleteUnpaid(List.of(payment));
    }

    // Xóa các khoản đã chọn: khoản đã thu được giữ lại (báo số lượng), không chặn cả nhóm
    @Transactional
    public BulkResultResponse bulkDelete(List<Long> ids) {
        List<Payment> payments = findPayments(ids);
        int deleted = deleteUnpaid(payments);
        return new BulkResultResponse(deleted, payments.size() - deleted);
    }

    // PAID: ghi hình thức + thời điểm thu; UNPAID (hoàn tác): xóa 2 cột đó
    @Transactional
    public PaymentResponse setStatus(Long id, PaymentStatus status, PaymentMethod method) {
        Payment payment = findPayment(id);
        if (payment.getStatus() == status) {
            throw new BusinessException(status == PaymentStatus.PAID
                    ? "Khoản này đã được ghi nhận đã thu" : "Khoản này đang ở trạng thái chưa thu");
        }
        if (status == PaymentStatus.PAID) {
            collect(List.of(payment), method);
        } else {
            payment.setStatus(PaymentStatus.UNPAID);
            payment.setPaymentMethod(null);
            payment.setPaidAt(null);
        }
        return PaymentResponse.from(payment);
    }

    // Thu tiền các khoản đã chọn cùng 1 hình thức; khoản đã thu rồi thì bỏ qua
    @Transactional
    public BulkResultResponse bulkCollect(List<Long> ids, PaymentMethod method) {
        List<Payment> payments = findPayments(ids);
        int collected = collect(payments, method);
        return new BulkResultResponse(collected, payments.size() - collected);
    }

    // ---------------------------------------------------------------- Quy tắc dùng chung

    // Tạo khoản cho users trong đợt: không trùng (phí tháng: không trùng tháng; thu thêm: không trùng trong đợt),
    // phí tháng theo giới tính (mức 0 ₫ thì báo lỗi), thu thêm theo số tiền của đợt
    private List<Payment> buildPayments(PaymentBatch batch, List<User> users, User creator) {
        boolean monthly = batch.getPaymentType() == PaymentType.MONTHLY;
        Set<Long> existing = monthly
                ? paymentRepository.findUserIdsByPeriod(PaymentType.MONTHLY, batch.getYear(), batch.getMonth())
                : paymentRepository.findUserIdsByBatch(batch.getId());
        List<User> duplicated = users.stream().filter(u -> existing.contains(u.getId())).toList();
        if (!duplicated.isEmpty()) {
            throw new BusinessException("userIds", (monthly ? "Đã có phí tháng " + period(batch.getMonth(), batch.getYear())
                    : "Đã có trong đợt thu này") + ": " + names(duplicated));
        }

        Map<Gender, BigDecimal> fees = new EnumMap<>(Gender.class);
        List<Payment> payments = new ArrayList<>();
        for (User user : users) {
            BigDecimal amount = batch.getAmount();
            if (monthly) {
                amount = fees.computeIfAbsent(user.getGender(), g -> monthlyFee(g, batch.getMonth(), batch.getYear()));
                if (amount.signum() == 0) {
                    throw new BusinessException("Mức phí tháng " + period(batch.getMonth(), batch.getYear())
                            + " cho thành viên " + GENDER_NAMES.get(user.getGender()) + " là 0 ₫, không cần tạo khoản thu");
                }
            }
            payments.add(newPayment(batch, user, amount, creator));
        }
        return payments;
    }

    // Xóa các khoản chưa thu trong danh sách (bỏ qua khoản đã thu); đợt nào hết người thì xóa đợt
    private int deleteUnpaid(List<Payment> payments) {
        List<Payment> unpaid = payments.stream().filter(p -> p.getStatus() == PaymentStatus.UNPAID).toList();
        Set<PaymentBatch> batches = unpaid.stream().map(Payment::getBatch).collect(Collectors.toSet());
        paymentRepository.deleteAll(unpaid);
        paymentRepository.flush();
        for (PaymentBatch batch : batches) {
            if (!paymentRepository.existsByBatch_Id(batch.getId())) batchRepository.delete(batch);
        }
        return unpaid.size();
    }

    // Ghi nhận đã thu các khoản chưa thu trong danh sách; trả số khoản đã thu
    private static int collect(List<Payment> payments, PaymentMethod method) {
        if (method == null) {
            throw new BusinessException("paymentMethod", "Vui lòng chọn hình thức thanh toán");
        }
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.PAID) continue;
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaymentMethod(method);
            payment.setPaidAt(now);
            count++;
        }
        return count;
    }

    // Đợt phí tháng của tháng đó (mỗi tháng 1 đợt), chưa có thì tạo
    private PaymentBatch monthlyBatch(int month, int year, User creator) {
        return batchRepository.findByPaymentTypeAndYearAndMonth(PaymentType.MONTHLY, year, month).orElseGet(() -> {
            PaymentBatch batch = new PaymentBatch();
            batch.setPaymentType(PaymentType.MONTHLY);
            batch.setMonth(month);
            batch.setYear(year);
            batch.setDescription("Phí tháng " + period(month, year));
            batch.setCreatedBy(creator);
            return batchRepository.save(batch);
        });
    }

    private static Payment newPayment(PaymentBatch batch, User user, BigDecimal amount, User creator) {
        Payment payment = new Payment();
        payment.setBatch(batch);
        payment.setUser(user);
        payment.setPaymentType(batch.getPaymentType());
        payment.setMonth(batch.getMonth());
        payment.setYear(batch.getYear());
        payment.setAmount(amount);
        payment.setCreatedBy(creator);
        return payment;
    }

    // Mức phí tháng áp dụng tại ngày 1 của tháng thu
    private BigDecimal monthlyFee(Gender gender, int month, int year) {
        LocalDate date = LocalDate.of(year, month, 1);
        return feeSettingService.findEffective(FeeType.MONTHLY, gender, date)
                .map(FeeSetting::getAmount)
                .orElseThrow(() -> new BusinessException("Chưa có mức phí tháng cho thành viên "
                        + GENDER_NAMES.get(gender) + " áp dụng đến ngày " + date.format(DATE)
                        + ". Vui lòng thêm ở trang Mức phí"));
    }

    // Chỉ tạo khoản thu cho thành viên đang hoạt động; id trùng trong danh sách tính 1 lần
    private List<User> findActiveUsers(List<Long> userIds) {
        Set<Long> ids = new LinkedHashSet<>(userIds);
        List<User> users = userRepository.findAllById(ids);
        if (users.size() != ids.size()) {
            throw new BusinessException("userIds", "Có thành viên không tồn tại, vui lòng tải lại danh sách");
        }
        List<User> inactive = users.stream().filter(u -> u.getStatus() != UserStatus.ACTIVE).toList();
        if (!inactive.isEmpty()) {
            throw new BusinessException("userIds",
                    "Chỉ tạo khoản thu cho thành viên đang hoạt động. Không hợp lệ: " + names(inactive));
        }
        return users.stream().sorted(Comparator.comparing(User::getFullName)).toList();
    }

    // Lọc đợt thu (dùng cho cả danh sách đợt và khoản thu theo đợt). Lọc tháng: phí tháng theo kỳ thu,
    // thu thêm (không có kỳ) theo tháng tạo đợt.
    private static Predicate batchFilter(Path<PaymentBatch> batch, CriteriaBuilder cb, String keyword,
                                         PaymentType paymentType, Integer month, Integer year) {
        List<Predicate> predicates = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            predicates.add(cb.like(batch.get("description"), "%" + escapeLike(keyword.trim()) + "%", '\\'));
        }
        if (paymentType != null) predicates.add(cb.equal(batch.get("paymentType"), paymentType));
        if (month != null || year != null) {
            List<Predicate> monthly = new ArrayList<>();
            List<Predicate> extra = new ArrayList<>();
            monthly.add(cb.equal(batch.get("paymentType"), PaymentType.MONTHLY));
            extra.add(cb.equal(batch.get("paymentType"), PaymentType.EXTRA));
            if (month != null) {
                monthly.add(cb.equal(batch.get("month"), month));
                extra.add(cb.equal(cb.function("month", Integer.class, batch.get("createdAt")), month));
            }
            if (year != null) {
                monthly.add(cb.equal(batch.get("year"), year));
                extra.add(cb.equal(cb.function("year", Integer.class, batch.get("createdAt")), year));
            }
            predicates.add(cb.or(cb.and(monthly.toArray(Predicate[]::new)), cb.and(extra.toArray(Predicate[]::new))));
        }
        return cb.and(predicates.toArray(Predicate[]::new));
    }

    private record Totals(long paidCount, BigDecimal paidAmount, long unpaidCount, BigDecimal unpaidAmount) {
    }

    // Đếm và cộng tiền theo trạng thái thu, cùng điều kiện lọc với danh sách
    private Totals totals(Specification<Payment> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Payment> root = query.from(Payment.class);
        Predicate where = spec.toPredicate(root, query, cb);
        query.select(cb.tuple(
                root.get("status").alias("status"),
                cb.count(root).alias("count"),
                cb.sum(root.<BigDecimal>get("amount")).alias("amount")));
        if (where != null) query.where(where);
        query.groupBy(root.get("status"));

        long paidCount = 0, unpaidCount = 0;
        BigDecimal paidAmount = BigDecimal.ZERO, unpaidAmount = BigDecimal.ZERO;
        for (Tuple t : entityManager.createQuery(query).getResultList()) {
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
        return new Totals(paidCount, paidAmount, unpaidCount, unpaidAmount);
    }

    private Map<Long, BatchStatsView> statsOf(Collection<Long> batchIds) {
        if (batchIds.isEmpty()) return Map.of();
        return paymentRepository.statsByBatches(batchIds, PaymentStatus.PAID).stream()
                .collect(Collectors.toMap(BatchStatsView::getBatchId, Function.identity()));
    }

    private PaymentBatchResponse toResponse(PaymentBatch batch) {
        paymentRepository.flush();
        return PaymentBatchResponse.from(batch, statsOf(List.of(batch.getId())).get(batch.getId()));
    }

    private PaymentBatch findBatch(Long id) {
        return batchRepository.findWithCreatorById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đợt thu"));
    }

    private Payment findPayment(Long id) {
        return paymentRepository.findWithUserById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khoản thu"));
    }

    // Khoản không tồn tại (vd người khác vừa xóa) -> 404, để giao diện tải lại danh sách
    private List<Payment> findPayments(List<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>(ids);
        List<Payment> payments = paymentRepository.findByIdIn(unique);
        if (payments.size() != unique.size()) {
            throw new ResourceNotFoundException("Có khoản thu không còn tồn tại, vui lòng tải lại danh sách");
        }
        return payments;
    }

    private static void ensureUnpaid(Payment payment, String action) {
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new BusinessException("Khoản này đã thu tiền, hãy hoàn tác về chưa thu trước khi " + action);
        }
    }

    // "A, B, C và 4 người khác"
    private static String names(List<User> users) {
        int shown = Math.min(users.size(), 3);
        String text = String.join(", ", users.stream().limit(shown).map(User::getFullName).toList());
        return users.size() > shown ? text + " và " + (users.size() - shown) + " người khác" : text;
    }

    private static String period(int month, int year) {
        return month + "/" + year;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
