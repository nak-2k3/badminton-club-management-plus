package com.badmintonclub.clubmanagement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// Địa điểm / nhà thi đấu, không phải 1 sân đơn lẻ
@Entity
@Table(name = "courts")
@Getter
@Setter
@NoArgsConstructor
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "court_id")
    private Long id;

    @Column(name = "court_name", nullable = false, length = 100)
    private String courtName;

    @Column(name = "address", nullable = false)
    private String address;

    // Giá thuê 1 sân / 1 giờ
    @Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "note")
    private String note;
}
