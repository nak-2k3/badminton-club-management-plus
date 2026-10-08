package com.badmintonclub.clubmanagement.entity;

import com.badmintonclub.clubmanagement.enums.PaymentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Đợt thu: gom các khoản thu tạo cùng lúc. Phí tháng: mỗi tháng 1 đợt; thu thêm: mỗi lần tạo 1 đợt.
@Entity
@Table(name = "payment_batches",
        uniqueConstraints = @UniqueConstraint(name = "uk_batch_period", columnNames = {"payment_type", "year", "month"}))
@Getter
@Setter
@NoArgsConstructor
public class PaymentBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Column(name = "description", nullable = false)
    private String description;

    // MONTHLY bắt buộc có month (1-12) và year; EXTRA để NULL
    @Column(name = "month", columnDefinition = "TINYINT")
    private Integer month;

    @Column(name = "year", columnDefinition = "SMALLINT")
    private Integer year;

    // EXTRA: số tiền mỗi người (dùng khi thêm người vào đợt); MONTHLY: NULL (theo giới tính)
    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "note")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
