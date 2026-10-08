package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.PaymentBatch;
import com.badmintonclub.clubmanagement.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, Long>, JpaSpecificationExecutor<PaymentBatch> {

    // Đợt phí tháng của 1 tháng (mỗi tháng 1 đợt — khớp unique uk_batch_period)
    Optional<PaymentBatch> findByPaymentTypeAndYearAndMonth(PaymentType paymentType, Integer year, Integer month);

    @EntityGraph(attributePaths = {"createdBy"})
    Optional<PaymentBatch> findWithCreatorById(Long id);

    // Danh sách đợt thu: nạp sẵn người tạo
    @Override
    @EntityGraph(attributePaths = {"createdBy"})
    Page<PaymentBatch> findAll(Specification<PaymentBatch> spec, Pageable pageable);
}
