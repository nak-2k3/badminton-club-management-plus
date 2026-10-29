package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.dto.expense.ExpenseListResponse;
import com.badmintonclub.clubmanagement.dto.expense.ExpenseRequest;
import com.badmintonclub.clubmanagement.dto.expense.ExpenseResponse;
import com.badmintonclub.clubmanagement.enums.ExpenseCategory;
import com.badmintonclub.clubmanagement.security.CurrentUser;
import com.badmintonclub.clubmanagement.service.ExpenseService;
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

// Khoản chi: ADMIN, TREASURER
@RestController
@RequestMapping("/api/expenses")
@PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    // VD: GET /api/expenses?keyword=cầu&category=SESSION&from=01/10/2026&to=31/10/2026&scheduleId=5&page=0&size=10
    @GetMapping
    public ExpenseListResponse search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) @DateTimeFormat(pattern = DateFormats.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = DateFormats.DATE) LocalDate to,
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "expenseDate") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return expenseService.search(keyword, category, from, to, scheduleId, page, size, sort, direction);
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.create(request, CurrentUser.id(jwt)));
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
