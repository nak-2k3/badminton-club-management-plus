package com.badmintonclub.clubmanagement.dto.payment;

// Xóa đợt thu: xóa mọi khoản chưa thu; còn người đã nộp thì giữ lại họ và giữ đợt
public record BatchDeleteResponse(
        int deleted,
        int keptPaid,
        boolean batchDeleted
) {
}
