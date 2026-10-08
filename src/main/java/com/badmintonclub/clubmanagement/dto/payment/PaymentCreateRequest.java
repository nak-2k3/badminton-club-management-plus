package com.badmintonclub.clubmanagement.dto.payment;

import com.badmintonclub.clubmanagement.enums.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

// Thêm khoản thu cho 1 hoặc nhiều thành viên (mỗi người 1 khoản, cùng nội dung).
// MONTHLY: bắt buộc month + year, vào đợt phí tháng của tháng đó; số tiền lấy từ mức phí (bỏ qua amount, description).
// EXTRA: tạo 1 đợt thu mới, bắt buộc description + amount, bỏ qua month/year (kiểm tra ở PaymentService).
public record PaymentCreateRequest(
        @NotEmpty(message = "Vui lòng chọn ít nhất 1 thành viên")
        @Size(max = 500, message = "Chọn tối đa 500 thành viên mỗi lần")
        List<@NotNull(message = "Thành viên không hợp lệ") Long> userIds,

        @NotNull(message = "Vui lòng chọn loại khoản thu")
        PaymentType paymentType,

        @Min(value = 1, message = "Tháng không hợp lệ")
        @Max(value = 12, message = "Tháng không hợp lệ")
        Integer month,

        @Min(value = 2000, message = "Năm không hợp lệ")
        @Max(value = 2100, message = "Năm không hợp lệ")
        Integer year,

        @Size(max = 255, message = "Nội dung tối đa 255 ký tự")
        String description,

        @DecimalMin(value = "0", inclusive = false, message = "Số tiền phải lớn hơn 0")
        @Digits(integer = 10, fraction = 2, message = "Số tiền không hợp lệ")
        BigDecimal amount,

        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note
) {
}
