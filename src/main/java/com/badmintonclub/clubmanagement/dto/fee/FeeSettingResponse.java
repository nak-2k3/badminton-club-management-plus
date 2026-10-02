package com.badmintonclub.clubmanagement.dto.fee;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.entity.FeeSetting;
import com.badmintonclub.clubmanagement.enums.FeeType;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FeeSettingResponse(
        Long id,
        FeeType feeType,
        Gender gender,
        BigDecimal amount,
        @JsonFormat(pattern = DateFormats.DATE) LocalDate effectiveFrom,
        boolean active,
        // true nếu đây là mức đang áp dụng hôm nay cho loại phí + giới tính này
        boolean current
) {
    public static FeeSettingResponse from(FeeSetting fee, boolean current) {
        return new FeeSettingResponse(
                fee.getId(),
                fee.getFeeType(),
                fee.getGender(),
                fee.getAmount(),
                fee.getEffectiveFrom(),
                Boolean.TRUE.equals(fee.getActive()),
                current);
    }
}
