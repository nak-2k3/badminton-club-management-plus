package com.badmintonclub.clubmanagement.controller;

import com.badmintonclub.clubmanagement.dto.common.OptionResponse;
import com.badmintonclub.clubmanagement.repository.LevelRepository;
import com.badmintonclub.clubmanagement.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Dữ liệu danh mục cho ô chọn trên giao diện (mọi người đã đăng nhập đều xem được).
// Truy vấn đơn giản, không đụng quan hệ LAZY nên không cần service/transaction riêng.
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final RoleRepository roleRepository;
    private final LevelRepository levelRepository;

    @GetMapping("/roles")
    public List<OptionResponse> roles() {
        return roleRepository.findAll(Sort.by("id")).stream().map(OptionResponse::from).toList();
    }

    @GetMapping("/levels")
    public List<OptionResponse> levels() {
        return levelRepository.findAll(Sort.by("id")).stream().map(OptionResponse::from).toList();
    }
}
