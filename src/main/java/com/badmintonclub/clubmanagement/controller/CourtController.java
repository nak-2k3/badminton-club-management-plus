package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.common.ActiveRequest;
import com.badmintonclub.clubmanagement.dto.court.CourtRequest;
import com.badmintonclub.clubmanagement.dto.court.CourtResponse;
import com.badmintonclub.clubmanagement.service.CourtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Xem: mọi người đã đăng nhập (cần khi xem lịch chơi). Thêm/sửa/xóa: ADMIN.
@RestController
@RequestMapping("/api/courts")
@RequiredArgsConstructor
public class CourtController {

    private final CourtService courtService;

    // VD: GET /api/courts?keyword=tampo&active=true
    @GetMapping
    public List<CourtResponse> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Boolean active) {
        return courtService.list(keyword, active);
    }

    @GetMapping("/{id}")
    public CourtResponse getById(@PathVariable Long id) {
        return courtService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CourtResponse> create(@Valid @RequestBody CourtRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courtService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CourtResponse update(@PathVariable Long id, @Valid @RequestBody CourtRequest request) {
        return courtService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public CourtResponse setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        return courtService.setActive(id, request.active());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        courtService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
