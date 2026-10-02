package com.badmintonclub.clubmanagement.dto.fee;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.enums.FeeType;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

// Mức phí đang áp dụng cho 1 nhóm (phí tháng nam / phí tháng nữ / phí khách).
// amount, effectiveFrom = null nếu nhóm này chưa có mức phí nào hiệu lực.
public record CurrentFeeResponse(
        FeeType feeType,
        Gender gender,
        Long feeSettingId,
        BigDecimal amount,
        @JsonFormat(pattern = DateFormats.DATE) LocalDate effectiveFrom
) {
}
