package com.badmintonclub.clubmanagement.dto.court;

import com.badmintonclub.clubmanagement.entity.Court;

import java.math.BigDecimal;

public record CourtResponse(
        Long id,
        String courtName,
        String address,
        BigDecimal hourlyRate,
        String phone,
        boolean active,
        String note
) {
    public static CourtResponse from(Court court) {
        return new CourtResponse(
                court.getId(),
                court.getCourtName(),
                court.getAddress(),
                court.getHourlyRate(),
                court.getPhone(),
                Boolean.TRUE.equals(court.getActive()),
                court.getNote());
    }
}
