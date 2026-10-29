package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse;
import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse.Debtor;
import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse.ExpenseLine;
import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse.IncomeLine;
import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse.MonthPoint;
import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse.Outstanding;
import com.badmintonclub.clubmanagement.entity.Expense;
import com.badmintonclub.clubmanagement.entity.GuestRegistration;
import com.badmintonclub.clubmanagement.entity.Payment;
import com.badmintonclub.clubmanagement.entity.User;
import com.badmintonclub.clubmanagement.enums.ExpenseCategory;
import com.badmintonclub.clubmanagement.enums.PaymentStatus;
import com.badmintonclub.clubmanagement.enums.PaymentType;
import com.badmintonclub.clubmanagement.enums.ScheduleStatus;
import com.badmintonclub.clubmanagement.exception.BusinessException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Báo cáo thu chi (ADMIN, TREASURER). Theo quy tắc nghiệp vụ:
 * - Thu = payments + guest_registrations đã PAID, tính theo ngày thu tiền (paid_at) — tiền thực vào quỹ.
 * - Chi = expenses theo ngày chi (expense_date), nhóm theo category.
 * - Tồn đầu kỳ = thu − chi trước ngày bắt đầu; tồn cuối kỳ = tồn đầu kỳ + thu − chi trong kỳ.
 * - Công nợ = khoản thu + phí khách chưa thu tại thời điểm xem (phí khách tính cho người dẫn; bỏ khách của buổi đã hủy
 *   vì buổi đã hủy không thu phí khách được).
 * Màn hình (summary) và file Excel (ReportExcelWriter) dùng chung 1 bộ số liệu load() để luôn khớp nhau.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    // Biểu đồ theo tháng: giới hạn độ dài kỳ để truy vấn và biểu đồ không quá lớn
    static final int MAX_MONTHS = 60;

    private final EntityManager entityManager;

    // Toàn bộ số liệu của 1 kỳ — dùng cho cả màn hình lẫn file Excel
    public record ReportData(ReportSummaryResponse summary, List<Payment> paidPayments,
                             List<GuestRegistration> paidGuests, List<Expense> expenses,
                             List<Payment> unpaidPayments, List<GuestRegistration> unpaidGuests) {
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse summary(LocalDate from, LocalDate to) {
        return load(from, to, false).summary();
    }

    @Transactional(readOnly = true)
    public ReportData load(LocalDate from, LocalDate to, boolean withDetails) {
        validate(from, to);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        // ---- Thu trong kỳ
        Map<PaymentType, long[]> paymentCounts = new EnumMap<>(PaymentType.class);
        Map<PaymentType, BigDecimal> paymentAmounts = new EnumMap<>(PaymentType.class);
        for (Object[] row : entityManager.createQuery("""
                        select p.paymentType, count(p), coalesce(sum(p.amount), 0) from Payment p
                        where p.status = :paid and p.paidAt >= :start and p.paidAt < :end
                        group by p.paymentType""", Object[].class)
                .setParameter("paid", PaymentStatus.PAID).setParameter("start", start).setParameter("end", end)
                .getResultList()) {
            paymentCounts.put((PaymentType) row[0], new long[]{(Long) row[1]});
            paymentAmounts.put((PaymentType) row[0], (BigDecimal) row[2]);
        }
        Object[] guestRow = entityManager.createQuery("""
                        select count(g), coalesce(sum(g.fee), 0) from GuestRegistration g
                        where g.paymentStatus = :paid and g.paidAt >= :start and g.paidAt < :end""", Object[].class)
                .setParameter("paid", PaymentStatus.PAID).setParameter("start", start).setParameter("end", end)
                .getSingleResult();
        List<IncomeLine> income = List.of(
                incomeLine("MONTHLY", paymentCounts.get(PaymentType.MONTHLY), paymentAmounts.get(PaymentType.MONTHLY)),
                incomeLine("EXTRA", paymentCounts.get(PaymentType.EXTRA), paymentAmounts.get(PaymentType.EXTRA)),
                new IncomeLine("GUEST", (Long) guestRow[0], (BigDecimal) guestRow[1]));
        BigDecimal totalIncome = income.stream().map(IncomeLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // ---- Chi trong kỳ (đủ mọi loại, kể cả 0)
        Map<ExpenseCategory, ExpenseLine> expenseLines = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory c : ExpenseCategory.values()) expenseLines.put(c, new ExpenseLine(c, 0, BigDecimal.ZERO));
        for (Object[] row : entityManager.createQuery("""
                        select e.category, count(e), coalesce(sum(e.amount), 0) from Expense e
                        where e.expenseDate >= :from and e.expenseDate <= :to
                        group by e.category""", Object[].class)
                .setParameter("from", from).setParameter("to", to).getResultList()) {
            ExpenseCategory c = (ExpenseCategory) row[0];
            expenseLines.put(c, new ExpenseLine(c, (Long) row[1], (BigDecimal) row[2]));
        }
        List<ExpenseLine> expense = List.copyOf(expenseLines.values());
        BigDecimal totalExpense = expense.stream().map(ExpenseLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // ---- Tồn đầu kỳ
        BigDecimal paidBefore = sum("""
                select coalesce(sum(p.amount), 0) from Payment p where p.status = :paid and p.paidAt < :start""", start);
        BigDecimal guestBefore = sum("""
                select coalesce(sum(g.fee), 0) from GuestRegistration g where g.paymentStatus = :paid and g.paidAt < :start""",
                start);
        BigDecimal expenseBefore = entityManager.createQuery(
                        "select coalesce(sum(e.amount), 0) from Expense e where e.expenseDate < :from", BigDecimal.class)
                .setParameter("from", from).getSingleResult();
        BigDecimal opening = paidBefore.add(guestBefore).subtract(expenseBefore);
        BigDecimal closing = opening.add(totalIncome).subtract(totalExpense);

        // ---- Theo tháng (đủ mọi tháng trong kỳ, kể cả tháng không có số liệu)
        Map<YearMonth, BigDecimal[]> months = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            months.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        addMonthly(months, 0, """
                select extract(year from p.paidAt), extract(month from p.paidAt), sum(p.amount) from Payment p
                where p.status = :paid and p.paidAt >= :start and p.paidAt < :end
                group by extract(year from p.paidAt), extract(month from p.paidAt)""", start, end, true);
        addMonthly(months, 0, """
                select extract(year from g.paidAt), extract(month from g.paidAt), sum(g.fee) from GuestRegistration g
                where g.paymentStatus = :paid and g.paidAt >= :start and g.paidAt < :end
                group by extract(year from g.paidAt), extract(month from g.paidAt)""", start, end, true);
        addMonthly(months, 1, """
                select extract(year from e.expenseDate), extract(month from e.expenseDate), sum(e.amount) from Expense e
                where e.expenseDate >= :start and e.expenseDate < :end
                group by extract(year from e.expenseDate), extract(month from e.expenseDate)""", from, to.plusDays(1), false);
        List<MonthPoint> monthPoints = months.entrySet().stream()
                .map(e -> new MonthPoint(e.getKey().getYear(), e.getKey().getMonthValue(), e.getValue()[0], e.getValue()[1]))
                .toList();

        // ---- Công nợ hiện tại
        List<Payment> unpaidPayments = entityManager.createQuery("""
                        select p from Payment p join fetch p.user join fetch p.batch
                        where p.status = :unpaid order by p.user.fullName, p.year, p.month, p.id""", Payment.class)
                .setParameter("unpaid", PaymentStatus.UNPAID).getResultList();
        List<GuestRegistration> unpaidGuests = entityManager.createQuery("""
                        select g from GuestRegistration g join fetch g.guest join fetch g.schedule s left join fetch g.invitedBy
                        where g.paymentStatus = :unpaid and s.status <> :cancelled
                        order by s.playDate, g.id""", GuestRegistration.class)
                .setParameter("unpaid", PaymentStatus.UNPAID).setParameter("cancelled", ScheduleStatus.CANCELLED)
                .getResultList();
        Outstanding outstanding = outstanding(unpaidPayments, unpaidGuests);

        ReportSummaryResponse summary = new ReportSummaryResponse(from, to, opening, totalIncome, totalExpense, closing,
                income, expense, monthPoints, outstanding);
        if (!withDetails) {
            return new ReportData(summary, List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // ---- Chi tiết cho file Excel
        List<Payment> paidPayments = entityManager.createQuery("""
                        select p from Payment p join fetch p.user join fetch p.batch
                        where p.status = :paid and p.paidAt >= :start and p.paidAt < :end
                        order by p.paidAt, p.id""", Payment.class)
                .setParameter("paid", PaymentStatus.PAID).setParameter("start", start).setParameter("end", end)
                .getResultList();
        List<GuestRegistration> paidGuests = entityManager.createQuery("""
                        select g from GuestRegistration g join fetch g.guest join fetch g.schedule
                        left join fetch g.invitedBy left join fetch g.collectedBy
                        where g.paymentStatus = :paid and g.paidAt >= :start and g.paidAt < :end
                        order by g.paidAt, g.id""", GuestRegistration.class)
                .setParameter("paid", PaymentStatus.PAID).setParameter("start", start).setParameter("end", end)
                .getResultList();
        List<Expense> expenses = entityManager.createQuery("""
                        select e from Expense e left join fetch e.schedule join fetch e.createdBy
                        where e.expenseDate >= :from and e.expenseDate <= :to
                        order by e.expenseDate, e.id""", Expense.class)
                .setParameter("from", from).setParameter("to", to).getResultList();
        return new ReportData(summary, paidPayments, paidGuests, expenses, unpaidPayments, unpaidGuests);
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessException("to", "Ngày kết thúc phải từ ngày bắt đầu trở đi");
        }
        if (ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) >= MAX_MONTHS) {
            throw new BusinessException("to", "Kỳ báo cáo tối đa " + MAX_MONTHS + " tháng");
        }
    }

    private static IncomeLine incomeLine(String source, long[] count, BigDecimal amount) {
        return new IncomeLine(source, count != null ? count[0] : 0, amount != null ? amount : BigDecimal.ZERO);
    }

    private BigDecimal sum(String jpql, LocalDateTime start) {
        return entityManager.createQuery(jpql, BigDecimal.class)
                .setParameter("paid", PaymentStatus.PAID).setParameter("start", start).getSingleResult();
    }

    // Cộng số liệu theo tháng vào cột index (0 = thu, 1 = chi)
    private void addMonthly(Map<YearMonth, BigDecimal[]> months, int index, String jpql, Object start, Object end,
                            boolean paidOnly) {
        var query = entityManager.createQuery(jpql, Object[].class).setParameter("start", start).setParameter("end", end);
        if (paidOnly) query.setParameter("paid", PaymentStatus.PAID);
        for (Object[] row : query.getResultList()) {
            YearMonth m = YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
            BigDecimal[] values = months.get(m);
            if (values != null && row[2] != null) values[index] = values[index].add((BigDecimal) row[2]);
        }
    }

    // Gom công nợ theo thành viên: khoản thu chưa nộp + phí khách mình dẫn chưa thu; khách tự liên hệ gom 1 dòng
    private static Outstanding outstanding(List<Payment> unpaidPayments, List<GuestRegistration> unpaidGuests) {
        record Acc(Long userId, String name, String email, long[] counts, BigDecimal[] amounts) {
        }
        Map<Long, Acc> byUser = new LinkedHashMap<>();
        Acc selfContact = new Acc(null, "Khách tự liên hệ CLB", null, new long[2], new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        BigDecimal paymentTotal = BigDecimal.ZERO, guestTotal = BigDecimal.ZERO;

        for (Payment p : unpaidPayments) {
            User u = p.getUser();
            Acc acc = byUser.computeIfAbsent(u.getId(), id -> new Acc(id, u.getFullName(), u.getEmail(), new long[2],
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}));
            acc.counts()[0]++;
            acc.amounts()[0] = acc.amounts()[0].add(p.getAmount());
            paymentTotal = paymentTotal.add(p.getAmount());
        }
        for (GuestRegistration g : unpaidGuests) {
            User u = g.getInvitedBy();
            Acc acc = u == null ? selfContact : byUser.computeIfAbsent(u.getId(), id -> new Acc(id, u.getFullName(),
                    u.getEmail(), new long[2], new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}));
            acc.counts()[1]++;
            acc.amounts()[1] = acc.amounts()[1].add(g.getFee());
            guestTotal = guestTotal.add(g.getFee());
        }

        List<Acc> all = new ArrayList<>(byUser.values());
        if (selfContact.counts()[1] > 0) all.add(selfContact);
        List<Debtor> debtors = all.stream()
                .map(a -> new Debtor(a.userId(), a.name(), a.email(), a.counts()[0], a.amounts()[0], a.counts()[1],
                        a.amounts()[1], a.amounts()[0].add(a.amounts()[1])))
                .sorted(Comparator.comparing(Debtor::total).reversed().thenComparing(Debtor::name))
                .toList();
        return new Outstanding(unpaidPayments.size(), paymentTotal, unpaidGuests.size(), guestTotal,
                paymentTotal.add(guestTotal), debtors);
    }
}
