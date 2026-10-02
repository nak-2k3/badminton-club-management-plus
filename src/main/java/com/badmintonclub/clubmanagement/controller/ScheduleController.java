package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.dto.common.PageResponse;
import com.badmintonclub.clubmanagement.dto.schedule.ScheduleRequest;
import com.badmintonclub.clubmanagement.dto.schedule.ScheduleResponse;
import com.badmintonclub.clubmanagement.dto.schedule.ScheduleStatusRequest;
import com.badmintonclub.clubmanagement.enums.ScheduleStatus;
import com.badmintonclub.clubmanagement.enums.ScheduleType;
import com.badmintonclub.clubmanagement.security.CurrentUser;
import com.badmintonclub.clubmanagement.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

// Xem: mọi người đã đăng nhập. Thêm/sửa/đổi trạng thái/xóa: ADMIN.
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    // VD: GET /api/schedules?from=01/10/2026&to=31/10/2026&status=OPEN&mine=true&page=0&size=10&sort=playDate&direction=asc
    // mine=true: chỉ các buổi người đang xem đã đăng ký
    @GetMapping
    public PageResponse<ScheduleResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = DateFormats.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = DateFormats.DATE) LocalDate to,
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(required = false) ScheduleType scheduleType,
            @RequestParam(required = false) Long courtId,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "playDate") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @AuthenticationPrincipal Jwt jwt) {
        return scheduleService.search(keyword, from, to, status, scheduleType, courtId, mine, CurrentUser.id(jwt),
                page, size, sort, direction);
    }

    @GetMapping("/{id}")
    public ScheduleResponse getById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return scheduleService.getById(id, CurrentUser.id(jwt));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScheduleResponse> create(@Valid @RequestBody ScheduleRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(request, CurrentUser.id(jwt)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleResponse update(@PathVariable Long id, @Valid @RequestBody ScheduleRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return scheduleService.update(id, request, CurrentUser.id(jwt));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ScheduleStatusRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return scheduleService.changeStatus(id, request.status(), request.reason(), CurrentUser.id(jwt));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
