package com.badmintonclub.clubmanagement.repository;

import java.math.BigDecimal;

// Thống kê 1 đợt thu: số người, số đã thu, tiền đã thu, tổng tiền
public interface BatchStatsView {
    Long getBatchId();

    Long getMemberCount();

    Long getPaidCount();

    BigDecimal getPaidAmount();

    BigDecimal getTotalAmount();
}
