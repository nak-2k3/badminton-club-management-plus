package com.badmintonclub.clubmanagement.dto.schedule;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.enums.ScheduleType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;

// Dùng chung cho thêm và sửa buổi chơi
public record ScheduleRequest(
        @NotBlank(message = "Vui lòng nhập tên buổi chơi")
        @Size(max = 150, message = "Tên buổi chơi tối đa 150 ký tự")
        String title,

        @NotNull(message = "Vui lòng chọn loại buổi chơi")
        ScheduleType scheduleType,

        @NotNull(message = "Vui lòng chọn sân")
        Long courtId,

        @NotNull(message = "Vui lòng nhập số sân thuê")
        @Min(value = 1, message = "Số sân thuê phải từ 1 trở lên")
        @Max(value = 50, message = "Số sân thuê tối đa 50")
        Integer courtCount,

        @NotNull(message = "Vui lòng chọn ngày chơi")
        @JsonFormat(pattern = DateFormats.DATE, lenient = OptBoolean.FALSE)
        LocalDate playDate,

        @NotNull(message = "Vui lòng chọn giờ bắt đầu")
        @JsonFormat(pattern = DateFormats.TIME, lenient = OptBoolean.FALSE)
        LocalTime startTime,

        @NotNull(message = "Vui lòng chọn giờ kết thúc")
        @JsonFormat(pattern = DateFormats.TIME, lenient = OptBoolean.FALSE)
        LocalTime endTime,

        @NotNull(message = "Vui lòng nhập số người tối đa")
        @Min(value = 1, message = "Số người tối đa phải từ 1 trở lên")
        @Max(value = 500, message = "Số người tối đa không quá 500")
        Integer maxPlayers,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note
) {
}
