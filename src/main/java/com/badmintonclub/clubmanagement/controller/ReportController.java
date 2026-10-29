package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.common.DateFormats;
import com.badmintonclub.clubmanagement.dto.report.ReportSummaryResponse;
import com.badmintonclub.clubmanagement.repository.UserRepository;
import com.badmintonclub.clubmanagement.security.CurrentUser;
import com.badmintonclub.clubmanagement.service.ReportExcelWriter;
import com.badmintonclub.clubmanagement.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// Báo cáo thu chi: ADMIN, TREASURER
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
@RequiredArgsConstructor
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ReportService reportService;
    private final ReportExcelWriter excelWriter;
    private final UserRepository userRepository;

    // VD: GET /api/reports/summary?from=01/10/2026&to=31/10/2026
    @GetMapping("/summary")
    public ReportSummaryResponse summary(
            @RequestParam @DateTimeFormat(pattern = DateFormats.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(pattern = DateFormats.DATE) LocalDate to) {
        return reportService.summary(from, to);
    }

    // Tải file Excel cùng số liệu với màn hình Báo cáo
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(pattern = DateFormats.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(pattern = DateFormats.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        String exportedBy = userRepository.findById(CurrentUser.id(jwt)).map(u -> u.getFullName()).orElse("");
        byte[] file = excelWriter.write(reportService.load(from, to, true), exportedBy);
        String filename = "bao-cao-thu-chi_" + from.format(FILE_DATE) + "_" + to.format(FILE_DATE) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(file);
    }
}
