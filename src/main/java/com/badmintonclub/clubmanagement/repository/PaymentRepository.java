package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.Payment;
import com.badmintonclub.clubmanagement.enums.PaymentStatus;
import com.badmintonclub.clubmanagement.enums.PaymentType;
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
import java.util.Set;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    // Người đã có phí tháng của tháng đó (khớp unique uk_payment_user_month)
    @Query("""
            select p.user.id from Payment p
            where p.paymentType = :type and p.year = :year and p.month = :month""")
    Set<Long> findUserIdsByPeriod(@Param("type") PaymentType type, @Param("year") Integer year,
                                  @Param("month") Integer month);

    // Người đã có trong đợt thu (thêm người vào đợt: không thêm trùng)
    @Query("select p.user.id from Payment p where p.batch.id = :batchId")
    Set<Long> findUserIdsByBatch(@Param("batchId") Long batchId);

    boolean existsByBatch_Id(Long batchId);

    @EntityGraph(attributePaths = {"user", "createdBy", "batch"})
    Optional<Payment> findWithUserById(Long id);

    // Thao tác hàng loạt theo danh sách id
    @EntityGraph(attributePaths = {"user", "createdBy", "batch"})
    List<Payment> findByIdIn(Collection<Long> ids);

    // Người trong 1 đợt thu, theo tên
    @EntityGraph(attributePaths = {"user", "createdBy", "batch"})
    List<Payment> findByBatch_IdOrderByUser_FullNameAscIdAsc(Long batchId);

    // "Khoản phí của tôi": mới nhất trước
    @EntityGraph(attributePaths = {"user", "createdBy", "batch"})
    List<Payment> findByUser_IdOrderByYearDescMonthDescCreatedAtDescIdDesc(Long userId);

    // Thống kê theo đợt (danh sách đợt thu, trang chi tiết đợt)
    @Query("""
            select p.batch.id as batchId, count(p) as memberCount,
                   sum(case when p.status = :paid then 1 else 0 end) as paidCount,
                   coalesce(sum(case when p.status = :paid then p.amount else 0 end), 0) as paidAmount,
                   coalesce(sum(p.amount), 0) as totalAmount
            from Payment p where p.batch.id in :batchIds
            group by p.batch.id""")
    List<BatchStatsView> statsByBatches(@Param("batchIds") Collection<Long> batchIds,
                                        @Param("paid") PaymentStatus paid);

    // Trang "Khoản thu" (tab Tất cả khoản): nạp sẵn thành viên, người tạo, đợt thu để map DTO
    @Override
    @EntityGraph(attributePaths = {"user", "createdBy", "batch"})
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);
}
