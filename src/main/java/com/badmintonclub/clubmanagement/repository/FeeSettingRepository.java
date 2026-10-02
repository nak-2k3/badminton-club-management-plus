package com.badmintonclub.clubmanagement.repository;

import com.badmintonclub.clubmanagement.entity.FeeSetting;
import com.badmintonclub.clubmanagement.enums.FeeType;
import com.badmintonclub.clubmanagement.enums.Gender;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Tham số gender = null được Spring Data dịch thành "gender IS NULL" (dùng cho phí khách)
public interface FeeSettingRepository extends JpaRepository<FeeSetting, Long> {

    List<FeeSetting> findAllByOrderByFeeTypeAscGenderAscEffectiveFromDescIdDesc();

    List<FeeSetting> findByFeeTypeOrderByGenderAscEffectiveFromDescIdDesc(FeeType feeType);

    // Mức phí đang áp dụng tại ngày `date`: dòng active, effective_from gần nhất và <= date
    Optional<FeeSetting> findFirstByFeeTypeAndGenderAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
            FeeType feeType, Gender gender, LocalDate date);

    boolean existsByFeeTypeAndGenderAndEffectiveFromAndActiveTrue(FeeType feeType, Gender gender, LocalDate effectiveFrom);

    boolean existsByFeeTypeAndGenderAndEffectiveFromAndActiveTrueAndIdNot(
            FeeType feeType, Gender gender, LocalDate effectiveFrom, Long id);
}
