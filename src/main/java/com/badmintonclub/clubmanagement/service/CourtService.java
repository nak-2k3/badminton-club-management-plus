package com.badmintonclub.clubmanagement.service;

import com.badmintonclub.clubmanagement.dto.court.CourtRequest;
import com.badmintonclub.clubmanagement.dto.court.CourtResponse;
import com.badmintonclub.clubmanagement.entity.Court;
import com.badmintonclub.clubmanagement.exception.BusinessException;
import com.badmintonclub.clubmanagement.exception.ResourceNotFoundException;
import com.badmintonclub.clubmanagement.repository.CourtRepository;
import com.badmintonclub.clubmanagement.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

// Sân = địa điểm / nhà thi đấu (giá hourly_rate tính cho 1 sân / 1 giờ)
@Service
@RequiredArgsConstructor
public class CourtService {

    private final CourtRepository courtRepository;
    private final ScheduleRepository scheduleRepository;

    // Số lượng sân ít -> trả cả danh sách, lọc trong bộ nhớ (không cần phân trang)
    @Transactional(readOnly = true)
    public List<CourtResponse> list(String keyword, Boolean active) {
        String key = StringUtils.hasText(keyword) ? normalize(keyword) : null;
        return courtRepository.findAll(Sort.by(Sort.Order.desc("active"), Sort.Order.asc("courtName"))).stream()
                .filter(c -> active == null || active.equals(c.getActive()))
                .filter(c -> key == null
                        || normalize(c.getCourtName()).contains(key)
                        || normalize(c.getAddress()).contains(key)
                        || (c.getPhone() != null && c.getPhone().contains(key)))
                .map(CourtResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourtResponse getById(Long id) {
        return CourtResponse.from(findCourt(id));
    }

    @Transactional
    public CourtResponse create(CourtRequest request) {
        String name = request.courtName().trim();
        if (courtRepository.existsByCourtName(name)) {
            throw new BusinessException("courtName", "Sân \"" + name + "\" đã tồn tại");
        }
        Court court = new Court();
        apply(court, request);
        return CourtResponse.from(courtRepository.save(court));
    }

    @Transactional
    public CourtResponse update(Long id, CourtRequest request) {
        Court court = findCourt(id);
        String name = request.courtName().trim();
        if (courtRepository.existsByCourtNameAndIdNot(name, id)) {
            throw new BusinessException("courtName", "Sân \"" + name + "\" đã tồn tại");
        }
        apply(court, request);
        return CourtResponse.from(court);
    }

    // Tạm ngưng: sân không còn được chọn khi tạo lịch chơi mới, lịch cũ giữ nguyên
    @Transactional
    public CourtResponse setActive(Long id, boolean active) {
        Court court = findCourt(id);
        court.setActive(active);
        return CourtResponse.from(court);
    }

    // Chỉ xóa được sân chưa từng có lịch chơi; đã có lịch thì dùng "tạm ngưng"
    @Transactional
    public void delete(Long id) {
        Court court = findCourt(id);
        if (scheduleRepository.existsByCourt_Id(id)) {
            throw new BusinessException("Sân \"" + court.getCourtName()
                    + "\" đã có lịch chơi nên không thể xóa. Hãy chuyển sang tạm ngưng.");
        }
        courtRepository.delete(court);
    }

    private void apply(Court court, CourtRequest request) {
        court.setCourtName(request.courtName().trim());
        court.setAddress(request.address().trim());
        court.setHourlyRate(request.hourlyRate());
        court.setPhone(trimToNull(request.phone()));
        court.setNote(trimToNull(request.note()));
    }

    private Court findCourt(Long id) {
        return courtRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sân"));
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    // Bỏ dấu + chữ thường để tìm kiếm "tampo" khớp "Sân Tampo" (giống collation của MySQL)
    private static String normalize(String value) {
        String noAccent = Normalizer.normalize(value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccent.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }
}
