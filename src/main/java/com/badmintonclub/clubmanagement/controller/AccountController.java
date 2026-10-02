package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.account.ChangeEmailRequest;
import com.badmintonclub.clubmanagement.dto.account.ChangePasswordRequest;
import com.badmintonclub.clubmanagement.dto.account.ProfileUpdateRequest;
import com.badmintonclub.clubmanagement.dto.member.MemberResponse;
import com.badmintonclub.clubmanagement.security.CurrentUser;
import com.badmintonclub.clubmanagement.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

// "Tài khoản của tôi": mọi người đã đăng nhập, chỉ thao tác trên chính tài khoản của mình (id lấy từ token)
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public MemberResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return accountService.getProfile(CurrentUser.id(jwt));
    }

    @PutMapping
    public MemberResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody ProfileUpdateRequest request) {
        return accountService.updateProfile(CurrentUser.id(jwt), request);
    }

    @PatchMapping("/email")
    public MemberResponse changeEmail(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody ChangeEmailRequest request) {
        return accountService.changeEmail(CurrentUser.id(jwt), request);
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(CurrentUser.id(jwt), request);
        return ResponseEntity.noContent().build();
    }
}
