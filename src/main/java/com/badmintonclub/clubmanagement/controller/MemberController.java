package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.common.PageResponse;
import com.badmintonclub.clubmanagement.dto.member.MemberCreateRequest;
import com.badmintonclub.clubmanagement.dto.member.MemberResponse;
import com.badmintonclub.clubmanagement.dto.member.MemberStatusRequest;
import com.badmintonclub.clubmanagement.dto.member.MemberUpdateRequest;
import com.badmintonclub.clubmanagement.dto.member.ResetPasswordRequest;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.badmintonclub.clubmanagement.enums.UserStatus;
import com.badmintonclub.clubmanagement.security.CurrentUser;
import com.badmintonclub.clubmanagement.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    // VD: GET /api/members?keyword=nguyen&status=ACTIVE&page=0&size=10&sort=fullName&direction=asc
    @GetMapping
    public PageResponse<MemberResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Long levelId,
            @RequestParam(required = false) Gender gender,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return memberService.search(keyword, status, roleId, levelId, gender, page, size, sort, direction);
    }

    @GetMapping("/{id}")
    public MemberResponse getById(@PathVariable Long id) {
        return memberService.getById(id);
    }

    @PostMapping
    public ResponseEntity<MemberResponse> create(@Valid @RequestBody MemberCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.create(request));
    }

    @PutMapping("/{id}")
    public MemberResponse update(@PathVariable Long id,
                                 @Valid @RequestBody MemberUpdateRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return memberService.update(id, request, CurrentUser.id(jwt));
    }

    @PatchMapping("/{id}/status")
    public MemberResponse changeStatus(@PathVariable Long id,
                                       @Valid @RequestBody MemberStatusRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return memberService.changeStatus(id, request.status(), CurrentUser.id(jwt));
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                              @Valid @RequestBody ResetPasswordRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        memberService.resetPassword(id, request.newPassword(), CurrentUser.id(jwt));
        return ResponseEntity.noContent().build();
    }
}
