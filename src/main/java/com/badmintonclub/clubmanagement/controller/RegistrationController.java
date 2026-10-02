package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.registration.AddParticipantRequest;
import com.badmintonclub.clubmanagement.dto.registration.AttendanceRequest;
import com.badmintonclub.clubmanagement.dto.registration.MarkAllAttendanceRequest;
import com.badmintonclub.clubmanagement.dto.registration.ParticipantResponse;
import com.badmintonclub.clubmanagement.dto.schedule.ScheduleResponse;
import com.badmintonclub.clubmanagement.security.CurrentUser;
import com.badmintonclub.clubmanagement.service.AttendanceService;
import com.badmintonclub.clubmanagement.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Đăng ký tham gia & điểm danh 1 buổi chơi.
// Xem người tham gia, tự đăng ký / tự hủy (/me): mọi người đã đăng nhập. Thêm/bớt người khác, điểm danh: ADMIN.
@RestController
@RequestMapping("/api/schedules/{scheduleId}")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;
    private final AttendanceService attendanceService;

    @GetMapping("/participants")
    public List<ParticipantResponse> participants(@PathVariable Long scheduleId) {
        return registrationService.participants(scheduleId);
    }

    @PostMapping("/registrations/me")
    public ScheduleResponse registerSelf(@PathVariable Long scheduleId, @AuthenticationPrincipal Jwt jwt) {
        return registrationService.registerSelf(scheduleId, CurrentUser.id(jwt));
    }

    @DeleteMapping("/registrations/me")
    public ScheduleResponse cancelSelf(@PathVariable Long scheduleId, @AuthenticationPrincipal Jwt jwt) {
        return registrationService.cancelSelf(scheduleId, CurrentUser.id(jwt));
    }

    @PostMapping("/registrations")
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleResponse addParticipant(@PathVariable Long scheduleId,
                                           @Valid @RequestBody AddParticipantRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        return registrationService.addParticipant(scheduleId, request.userId(), CurrentUser.id(jwt));
    }

    @DeleteMapping("/registrations/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ScheduleResponse removeParticipant(@PathVariable Long scheduleId, @PathVariable Long userId,
                                              @AuthenticationPrincipal Jwt jwt) {
        return registrationService.removeParticipant(scheduleId, userId, CurrentUser.id(jwt));
    }

    @PutMapping("/attendances/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ParticipantResponse markAttendance(@PathVariable Long scheduleId, @PathVariable Long userId,
                                              @Valid @RequestBody AttendanceRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        return attendanceService.mark(scheduleId, userId, request.status(), request.note(), CurrentUser.id(jwt));
    }

    @PostMapping("/attendances/mark-all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ParticipantResponse> markAll(@PathVariable Long scheduleId,
                                             @Valid @RequestBody MarkAllAttendanceRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        return attendanceService.markAll(scheduleId, request.status(), CurrentUser.id(jwt));
    }
}
