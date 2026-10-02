package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.fee.CurrentFeeResponse;
import com.badmintonclub.clubmanagement.dto.fee.FeeSettingRequest;
import com.badmintonclub.clubmanagement.dto.fee.FeeSettingResponse;
import com.badmintonclub.clubmanagement.entity.FeeSetting;
import com.badmintonclub.clubmanagement.enums.FeeType;
import com.badmintonclub.clubmanagement.enums.Gender;
import com.badmintonclub.clubmanagement.exception.BusinessException;
import com.badmintonclub.clubmanagement.exception.ResourceNotFoundException;
import com.badmintonclub.clubmanagement.repository.FeeSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mức phí: MONTHLY theo giới tính, GUEST một mức chung (gender = null).
 * Không sửa đè lịch sử khi đổi giá: thêm dòng mới với effective_from mới; mức đang áp dụng
 * là dòng active có effective_from gần nhất và <= ngày áp dụng (xem findEffective).
 */
@Service
@RequiredArgsConstructor
public class FeeSettingService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FeeSettingRepository feeSettingRepository;

    // Các nhóm phí cần có mức áp dụng: phí tháng nam, phí tháng nữ, phí khách
    private static List<FeeGroup> groups() {
        List<FeeGroup> groups = new ArrayList<>();
        for (Gender gender : Gender.values()) groups.add(new FeeGroup(FeeType.MONTHLY, gender));
        groups.add(new FeeGroup(FeeType.GUEST, null));
        return groups;
    }

    private record FeeGroup(FeeType feeType, Gender gender) {
    }

    // Dùng lại khi tạo học phí tháng / đăng ký khách
    @Transactional(readOnly = true)
    public Optional<FeeSetting> findEffective(FeeType feeType, Gender gender, LocalDate date) {
        return feeSettingRepository
                .findFirstByFeeTypeAndGenderAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                        feeType, feeType == FeeType.GUEST ? null : gender, date);
    }

    @Transactional(readOnly = true)
    public List<CurrentFeeResponse> current() {
        LocalDate today = LocalDate.now();
        return groups().stream().map(g -> findEffective(g.feeType(), g.gender(), today)
                        .map(f -> new CurrentFeeResponse(g.feeType(), g.gender(), f.getId(), f.getAmount(), f.getEffectiveFrom()))
                        .orElse(new CurrentFeeResponse(g.feeType(), g.gender(), null, null, null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeeSettingResponse> list(FeeType feeType) {
        LocalDate today = LocalDate.now();
        Set<Long> currentIds = groups().stream()
                .map(g -> findEffective(g.feeType(), g.gender(), today))
                .flatMap(Optional::stream)
                .map(FeeSetting::getId)
                .collect(Collectors.toSet());
        List<FeeSetting> fees = feeType == null
                ? feeSettingRepository.findAllByOrderByFeeTypeAscGenderAscEffectiveFromDescIdDesc()
                : feeSettingRepository.findByFeeTypeOrderByGenderAscEffectiveFromDescIdDesc(feeType);
        return fees.stream().map(f -> FeeSettingResponse.from(f, currentIds.contains(f.getId()))).toList();
    }

    @Transactional
    public FeeSettingResponse create(FeeSettingRequest request) {
        Gender gender = resolveGender(request);
        if (feeSettingRepository.existsByFeeTypeAndGenderAndEffectiveFromAndActiveTrue(
                request.feeType(), gender, request.effectiveFrom())) {
            throw duplicate(request.effectiveFrom());
        }
        FeeSetting fee = new FeeSetting();
        apply(fee, request, gender);
        return toResponse(feeSettingRepository.save(fee));
    }

    @Transactional
    public FeeSettingResponse update(Long id, FeeSettingRequest request) {
        FeeSetting fee = findFee(id);
        Gender gender = resolveGender(request);
        if (Boolean.TRUE.equals(fee.getActive()) && feeSettingRepository.existsByFeeTypeAndGenderAndEffectiveFromAndActiveTrueAndIdNot(
                request.feeType(), gender, request.effectiveFrom(), id)) {
            throw duplicate(request.effectiveFrom());
        }
        apply(fee, request, gender);
        return toResponse(fee);
    }

    // Ngưng áp dụng một mức phí (vd nhập nhầm); mức trước đó sẽ tự áp dụng lại
    @Transactional
    public FeeSettingResponse setActive(Long id, boolean active) {
        FeeSetting fee = findFee(id);
        if (active && !Boolean.TRUE.equals(fee.getActive())
                && feeSettingRepository.existsByFeeTypeAndGenderAndEffectiveFromAndActiveTrueAndIdNot(
                fee.getFeeType(), fee.getGender(), fee.getEffectiveFrom(), id)) {
            throw new BusinessException("Đã có mức phí khác đang áp dụng từ ngày "
                    + fee.getEffectiveFrom().format(DATE) + " cho nhóm này");
        }
        fee.setActive(active);
        return toResponse(fee);
    }

    // MONTHLY bắt buộc chọn giới tính; GUEST luôn lưu null (khớp CHECK chk_fee_gender trong DB)
    private static Gender resolveGender(FeeSettingRequest request) {
        if (request.feeType() == FeeType.GUEST) return null;
        if (request.gender() == null) {
            throw new BusinessException("gender", "Phí tháng phải chọn giới tính");
        }
        return request.gender();
    }

    private static BusinessException duplicate(LocalDate effectiveFrom) {
        return new BusinessException("effectiveFrom",
                "Đã có mức phí cùng loại áp dụng từ ngày " + effectiveFrom.format(DATE));
    }

    private static void apply(FeeSetting fee, FeeSettingRequest request, Gender gender) {
        fee.setFeeType(request.feeType());
        fee.setGender(gender);
        fee.setAmount(request.amount());
        fee.setEffectiveFrom(request.effectiveFrom());
    }

    private FeeSettingResponse toResponse(FeeSetting fee) {
        boolean current = findEffective(fee.getFeeType(), fee.getGender(), LocalDate.now())
                .map(f -> f.getId().equals(fee.getId()))
                .orElse(false);
        return FeeSettingResponse.from(fee, current);
    }

    private FeeSetting findFee(Long id) {
        return feeSettingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mức phí"));
    }
}
