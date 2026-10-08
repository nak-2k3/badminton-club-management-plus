package com.badmintonclub.clubmanagement.entity;

import com.badmintonclub.clubmanagement.enums.PaymentMethod;
import com.badmintonclub.clubmanagement.enums.PaymentStatus;
import com.badmintonclub.clubmanagement.enums.PaymentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_user_month",
                columnNames = {"user_id", "payment_type", "year", "month"}))
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    // Đợt thu chứa khoản này (nội dung khoản thu nằm ở đợt)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private PaymentBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    // Chép từ đợt thu; giữ ở đây để unique uk_payment_user_month chặn thu trùng tháng.
    // MONTHLY bắt buộc có month (1-12) và year
    @Column(name = "month", columnDefinition = "TINYINT")
    private Integer month;

    @Column(name = "year", columnDefinition = "SMALLINT")
    private Integer year;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status = PaymentStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "note")
    private String note;
}
