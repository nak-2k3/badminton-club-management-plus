package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.dto.guest.GuestAttendanceRequest;
import com.badmintonclub.clubmanagement.dto.guest.GuestFeeListResponse;
import com.badmintonclub.clubmanagement.dto.guest.GuestRegistrationResponse;
import com.badmintonclub.clubmanagement.dto.guest.GuestRequest;
import com.badmintonclub.clubmanagement.dto.guest.PaymentStatusRequest;
import com.badmintonclub.clubmanagement.enums.PaymentStatus;
import com.badmintonclub.clubmanagement.security.CurrentUser;
import com.badmintonclub.clubmanagement.service.AttendanceService;
import com.badmintonclub.clubmanagement.service.GuestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

// Khách vãng lai.
// Xem khách của buổi, dẫn khách, hủy khách: mọi người đã đăng nhập (service kiểm tra người dẫn / quyền).
// Thu phí khách, trang "Phí khách": ADMIN, TREASURER. Điểm danh khách: ADMIN.
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GuestController {

    private final GuestService guestService;
    private final AttendanceService attendanceService;

    @GetMapping("/schedules/{scheduleId}/guests")
    public List<GuestRegistrationResponse> list(@PathVariable Long scheduleId, @AuthenticationPrincipal Jwt jwt,
                                                Authentication authentication) {
        return guestService.list(scheduleId, CurrentUser.id(jwt), CurrentUser.isManager(authentication));
    }

    @PostMapping("/schedules/{scheduleId}/guests")
    public ResponseEntity<GuestRegistrationResponse> add(@PathVariable Long scheduleId,
                                                         @Valid @RequestBody GuestRequest request,
                                                         @AuthenticationPrincipal Jwt jwt,
                                                         Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                guestService.add(scheduleId, request, CurrentUser.id(jwt), CurrentUser.isManager(authentication)));
    }

    @PutMapping("/schedules/{scheduleId}/guests/{id}")
    public GuestRegistrationResponse update(@PathVariable Long scheduleId, @PathVariable Long id,
                                            @Valid @RequestBody GuestRequest request,
                                            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return guestService.update(scheduleId, id, request, CurrentUser.id(jwt), CurrentUser.isManager(authentication));
    }

    @DeleteMapping("/schedules/{scheduleId}/guests/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long scheduleId, @PathVariable Long id,
                                       @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        guestService.remove(scheduleId, id, CurrentUser.id(jwt), CurrentUser.isManager(authentication));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/schedules/{scheduleId}/guests/{id}/payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public GuestRegistrationResponse setPayment(@PathVariable Long scheduleId, @PathVariable Long id,
                                                @Valid @RequestBody PaymentStatusRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        return guestService.setPayment(scheduleId, id, request.status(), CurrentUser.id(jwt));
    }

    @PutMapping("/schedules/{scheduleId}/guests/{id}/attendance")
    @PreAuthorize("hasRole('ADMIN')")
    public GuestRegistrationResponse markAttendance(@PathVariable Long scheduleId, @PathVariable Long id,
                                                    @Valid @RequestBody GuestAttendanceRequest request) {
        return attendanceService.markGuest(scheduleId, id, request.status());
    }

    // VD: GET /api/guest-fees?from=01/10/2026&to=31/10/2026&paymentStatus=UNPAID&keyword=an&page=0&size=10
    @GetMapping("/guest-fees")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public GuestFeeListResponse searchFees(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = DateFormats.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = DateFormats.DATE) LocalDate to,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "playDate") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return guestService.searchFees(keyword, from, to, paymentStatus, includeCancelled, page, size, sort, direction);
    }
}
