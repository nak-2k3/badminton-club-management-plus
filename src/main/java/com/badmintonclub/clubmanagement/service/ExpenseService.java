package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.common.PageResponse;
import com.badmintonclub.clubmanagement.dto.expense.ExpenseListResponse;
import com.badmintonclub.clubmanagement.dto.expense.ExpenseListResponse.CategoryTotal;
import com.badmintonclub.clubmanagement.dto.expense.ExpenseRequest;
import com.badmintonclub.clubmanagement.dto.expense.ExpenseResponse;
import com.badmintonclub.clubmanagement.entity.Expense;
import com.badmintonclub.clubmanagement.entity.Schedule;
import com.badmintonclub.clubmanagement.enums.ExpenseCategory;
import com.badmintonclub.clubmanagement.exception.BusinessException;
import com.badmintonclub.clubmanagement.exception.ResourceNotFoundException;
import com.badmintonclub.clubmanagement.repository.ExpenseRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Khoản chi (ADMIN, TREASURER). Thêm và sửa đều đi qua apply() để cùng quy tắc:
 * - Chi phí buổi (SESSION = tiền sân + cầu) bắt buộc gắn buổi chơi, mỗi buổi chỉ 1 khoản (khóa dòng buổi khi kiểm tra
 *   để 2 người ghi cùng lúc không tạo trùng). Tiền sân gợi ý = ScheduleService.calculateCourtRent (giao diện tự điền
 *   rồi cộng tiền cầu), vẫn sửa được vì thực tế có thể giảm giá / phát sinh.
 * - Ngày chi không sau hôm nay. Buổi đã hủy vẫn ghi chi được (vd mất tiền cọc sân).
 * Khoản chi không có trạng thái nên sửa / xóa tự do.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("expenseDate", "amount", "createdAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final ExpenseRepository expenseRepository;
    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    // Danh sách + tổng chi theo từng loại. Tổng không phụ thuộc bộ lọc loại để luôn thấy đủ các loại.
    @Transactional(readOnly = true)
    public ExpenseListResponse search(String keyword, ExpenseCategory category, LocalDate from, LocalDate to,
                                      Long scheduleId, int page, int size, String sort, String direction) {
        Specification<Expense> base = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + escapeLike(keyword.trim()) + "%";
                predicates.add(cb.or(
                        cb.like(root.get("description"), pattern, '\\'),
                        cb.like(root.get("note"), pattern, '\\'),
                        cb.like(root.join("schedule", JoinType.LEFT).get("title"), pattern, '\\')));
            }
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            if (scheduleId != null) predicates.add(cb.equal(root.get("schedule").get("id"), scheduleId));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Specification<Expense> listSpec = category == null ? base
                : base.and((root, query, cb) -> cb.equal(root.get("category"), category));

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = SORTABLE_FIELDS.contains(sort) ? sort : "expenseDate";
        Sort order = Sort.by(dir, sortField).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), order);

        PageResponse<ExpenseResponse> result = PageResponse.from(expenseRepository.findAll(listSpec, pageable),
                ExpenseResponse::from);

        Map<ExpenseCategory, CategoryTotal> totals = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory c : ExpenseCategory.values()) totals.put(c, new CategoryTotal(c, 0, BigDecimal.ZERO));
        long totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Tuple t : totalsByCategory(base)) {
            ExpenseCategory c = t.get("category", ExpenseCategory.class);
            long count = t.get("count", Long.class);
            BigDecimal amount = t.get("amount", BigDecimal.class);
            amount = amount != null ? amount : BigDecimal.ZERO;
            totals.put(c, new CategoryTotal(c, count, amount));
            totalCount += count;
            totalAmount = totalAmount.add(amount);
        }
        return new ExpenseListResponse(result, totalCount, totalAmount, List.copyOf(totals.values()));
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest request, Long currentUserId) {
        Expense expense = new Expense();
        expense.setCreatedBy(userRepository.getReferenceById(currentUserId));
        apply(expense, request);
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse update(Long id, ExpenseRequest request) {
        Expense expense = findExpense(id);
        apply(expense, request);
        return ExpenseResponse.from(expense);
    }

    @Transactional
    public void delete(Long id) {
        expenseRepository.delete(findExpense(id));
    }

    private void apply(Expense expense, ExpenseRequest request) {
        if (request.expenseDate().isAfter(LocalDate.now())) {
            throw new BusinessException("expenseDate", "Ngày chi không được sau hôm nay");
        }
        Schedule schedule = null;
        if (request.scheduleId() != null) {
            // Khóa dòng buổi: 2 người cùng ghi chi phí cho 1 buổi không tạo trùng
            schedule = scheduleRepository.findByIdForUpdate(request.scheduleId())
                    .orElseThrow(() -> new BusinessException("scheduleId", "Buổi chơi không tồn tại"));
        }
        if (request.category() == ExpenseCategory.SESSION) {
            if (schedule == null) {
                throw new BusinessException("scheduleId", "Tiền sân + cầu phải chọn buổi chơi");
            }
            Long scheduleId = schedule.getId();
            expenseRepository.findFirstBySchedule_IdAndCategory(scheduleId, ExpenseCategory.SESSION)
                    .filter(existing -> !existing.getId().equals(expense.getId()))
                    .ifPresent(existing -> {
                        throw new BusinessException("scheduleId", "Buổi này đã có khoản tiền sân + cầu "
                                + formatMoney(existing.getAmount()) + ". Hãy sửa khoản đó thay vì thêm mới");
                    });
        }
        expense.setDescription(request.description().trim());
        expense.setCategory(request.category());
        expense.setSchedule(schedule);
        expense.setAmount(request.amount());
        expense.setExpenseDate(request.expenseDate());
        expense.setNote(trimToNull(request.note()));
    }

    // Đếm và cộng tiền theo loại chi, cùng điều kiện lọc với danh sách
    private List<Tuple> totalsByCategory(Specification<Expense> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Expense> root = query.from(Expense.class);
        Predicate where = spec.toPredicate(root, query, cb);
        query.select(cb.tuple(
                root.get("category").alias("category"),
                cb.count(root).alias("count"),
                cb.sum(root.<BigDecimal>get("amount")).alias("amount")));
        if (where != null) query.where(where);
        query.groupBy(root.get("category"));
        return entityManager.createQuery(query).getResultList();
    }

    private Expense findExpense(Long id) {
        return expenseRepository.findWithScheduleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khoản chi"));
    }

    private static String formatMoney(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
