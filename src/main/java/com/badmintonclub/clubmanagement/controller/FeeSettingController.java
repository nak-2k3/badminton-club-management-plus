package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.common.ActiveRequest;
import com.badmintonclub.clubmanagement.dto.fee.CurrentFeeResponse;
import com.badmintonclub.clubmanagement.dto.fee.FeeSettingRequest;
import com.badmintonclub.clubmanagement.dto.fee.FeeSettingResponse;
import com.badmintonclub.clubmanagement.enums.FeeType;
import com.badmintonclub.clubmanagement.service.FeeSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Xem: ADMIN, TREASURER. Thêm/sửa/bật tắt: ADMIN. Không xóa để giữ lịch sử giá.
@RestController
@RequestMapping("/api/fee-settings")
@PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
@RequiredArgsConstructor
public class FeeSettingController {

    private final FeeSettingService feeSettingService;

    @GetMapping
    public List<FeeSettingResponse> list(@RequestParam(required = false) FeeType feeType) {
        return feeSettingService.list(feeType);
    }

    // Mức đang áp dụng hôm nay: phí tháng nam, phí tháng nữ, phí khách
    @GetMapping("/current")
    public List<CurrentFeeResponse> current() {
        return feeSettingService.current();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeeSettingResponse> create(@Valid @RequestBody FeeSettingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feeSettingService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FeeSettingResponse update(@PathVariable Long id, @Valid @RequestBody FeeSettingRequest request) {
        return feeSettingService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public FeeSettingResponse setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        return feeSettingService.setActive(id, request.active());
    }
}
