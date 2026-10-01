package com.badmintonclub.clubmanagement.dto.common;

/**
 * Định dạng ngày giờ dùng trong JSON (@JsonFormat).
 * Dùng "uuuu" thay vì "yyyy": khi đọc với lenient = OptBoolean.FALSE, Jackson dùng ResolverStyle.STRICT,
 * mà ở chế độ STRICT "yyyy" (năm theo kỷ nguyên) bắt buộc phải có kỷ nguyên nên mọi ngày đều bị từ chối.
 * STRICT giúp từ chối ngày không có thật (31/02, 31/04...) thay vì tự đổi thành ngày cuối tháng.
 */
public final class DateFormats {

    public static final String DATE = "dd/MM/uuuu";
    public static final String DATE_TIME = "dd/MM/uuuu HH:mm";

    private DateFormats() {
    }
}
