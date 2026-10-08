package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.common.OptionResponse;
import com.badmintonclub.clubmanagement.dto.payment.BatchDeleteResponse;
import com.badmintonclub.clubmanagement.dto.payment.BatchMembersRequest;
import com.badmintonclub.clubmanagement.dto.payment.BulkPaymentRequest;
import com.badmintonclub.clubmanagement.dto.payment.BulkResultResponse;
import com.badmintonclub.clubmanagement.dto.payment.CollectPaymentRequest;
import com.badmintonclub.clubmanagement.dto.payment.MonthlyGenerateRequest;
import com.badmintonclub.clubmanagement.dto.payment.MonthlyGenerateResponse;
import com.badmintonclub.clubmanagement.dto.payment.PaymentBatchListResponse;
import com.badmintonclub.clubmanagement.dto.payment.PaymentBatchResponse;
import com.badmintonclub.clubmanagement.dto.payment.PaymentBatchUpdateRequest;
import com.badmintonclub.clubmanagement.dto.payment.PaymentCreateRequest;
import com.badmintonclub.clubmanagement.dto.payment.PaymentListResponse;
import com.badmintonclub.clubmanagement.dto.payment.PaymentResponse;
import com.badmintonclub.clubmanagement.dto.payment.PaymentUpdateRequest;
import com.badmintonclub.clubmanagement.enums.PaymentStatus;
import com.badmintonclub.clubmanagement.enums.PaymentType;
import com.badmintonclub.clubmanagement.security.CurrentUser;
import com.badmintonclub.clubmanagement.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Khoản thu của thành viên, gom theo đợt thu: ADMIN, TREASURER. "Khoản phí của tôi" (/me): mọi người đã đăng nhập, chỉ xem.
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/me")
    public List<PaymentResponse> myPayments(@AuthenticationPrincipal Jwt jwt) {
        return paymentService.myPayments(CurrentUser.id(jwt));
    }

    // VD: GET /api/payments?keyword=an&paymentType=MONTHLY&month=10&year=2026&status=UNPAID&page=0&size=10
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public PaymentListResponse search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PaymentType paymentType,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "period") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return paymentService.search(keyword, paymentType, status, month, year, page, size, sort, direction);
    }

    @GetMapping("/member-options")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public List<OptionResponse> memberOptions() {
        return paymentService.memberOptions();
    }

    // Tạo khoản thu cho 1 hoặc nhiều thành viên (userIds), mỗi người 1 khoản -> trả đợt thu chứa các khoản đó
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public ResponseEntity<PaymentBatchResponse> create(@Valid @RequestBody PaymentCreateRequest request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(request, CurrentUser.id(jwt)));
    }

    @PostMapping("/monthly")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public MonthlyGenerateResponse generateMonthly(@Valid @RequestBody MonthlyGenerateRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return paymentService.generateMonthly(request.month(), request.year(), CurrentUser.id(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public PaymentResponse update(@PathVariable Long id, @Valid @RequestBody PaymentUpdateRequest request) {
        return paymentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paymentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Xóa các khoản đã chọn (khoản đã thu được giữ lại)
    @PostMapping("/bulk-delete")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public BulkResultResponse bulkDelete(@Valid @RequestBody BulkPaymentRequest request) {
        return paymentService.bulkDelete(request.ids());
    }

    // Thu tiền các khoản đã chọn cùng 1 hình thức (khoản đã thu được bỏ qua)
    @PostMapping("/bulk-collect")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public BulkResultResponse bulkCollect(@Valid @RequestBody BulkPaymentRequest request) {
        return paymentService.bulkCollect(request.ids(), request.paymentMethod());
    }

    // ---- Đợt thu
    // VD: GET /api/payments/batches?keyword=áo&paymentType=EXTRA&month=10&year=2026&page=0&size=10
    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public PaymentBatchListResponse searchBatches(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PaymentType paymentType,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.searchBatches(keyword, paymentType, month, year, page, size);
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public PaymentBatchResponse getBatch(@PathVariable Long id) {
        return paymentService.getBatch(id);
    }

    @GetMapping("/batches/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public List<PaymentResponse> batchPayments(@PathVariable Long id) {
        return paymentService.batchPayments(id);
    }

    @PutMapping("/batches/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public PaymentBatchResponse updateBatch(@PathVariable Long id, @Valid @RequestBody PaymentBatchUpdateRequest request) {
        return paymentService.updateBatch(id, request);
    }

    @PostMapping("/batches/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public PaymentBatchResponse addBatchMembers(@PathVariable Long id, @Valid @RequestBody BatchMembersRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        return paymentService.addBatchMembers(id, request.userIds(), CurrentUser.id(jwt));
    }

    // Xóa mọi khoản chưa thu của đợt; còn người đã nộp thì giữ họ và giữ đợt
    @DeleteMapping("/batches/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public BatchDeleteResponse deleteBatch(@PathVariable Long id) {
        return paymentService.deleteBatch(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public PaymentResponse setStatus(@PathVariable Long id, @Valid @RequestBody CollectPaymentRequest request) {
        return paymentService.setStatus(id, request.status(), request.paymentMethod());
    }
}
