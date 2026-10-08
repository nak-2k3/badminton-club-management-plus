package com.badmintonclub.clubmanagement.dto.payment;

// Kết quả thao tác hàng loạt: số khoản đã xử lý, số khoản bỏ qua (vd đã thu thì không xóa / đã thu rồi thì không thu lại)
public record BulkResultResponse(
        int affected,
        int skipped
) {
}
