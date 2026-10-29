-- Gộp loại khoản chi: chỉ còn SESSION (tiền sân + cầu của 1 buổi, bắt buộc gắn buổi, mỗi buổi 1 khoản) và OTHER (khác).
-- Dữ liệu cũ: COURT_RENT + SHUTTLECOCK cùng buổi gộp thành 1 khoản SESSION (cộng tiền, chi tiết cũ ghi vào note);
-- SHUTTLECOCK không gắn buổi và EQUIPMENT chuyển thành OTHER.
-- Lưu ý: DDL của MySQL tự commit — sao lưu bảng expenses trước khi chạy.

ALTER TABLE `expenses`
  MODIFY `category` enum('COURT_RENT','SHUTTLECOCK','EQUIPMENT','OTHER','SESSION')
    COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER';

START TRANSACTION;

-- Mỗi buổi có tiền sân / tiền cầu: giữ dòng có id nhỏ nhất, cộng tiền, ghi lại chi tiết
CREATE TEMPORARY TABLE `tmp_session_merge` AS
SELECT e.`schedule_id`,
       MIN(e.`expense_id`) AS `keep_id`,
       SUM(e.`amount`) AS `total`,
       GROUP_CONCAT(CONCAT(e.`description`, ': ', FORMAT(e.`amount`, 0, 'vi_VN'), ' đ')
                    ORDER BY e.`expense_id` SEPARATOR ' + ') AS `detail`
FROM `expenses` e
WHERE e.`schedule_id` IS NOT NULL AND e.`category` IN ('COURT_RENT', 'SHUTTLECOCK')
GROUP BY e.`schedule_id`;

UPDATE `expenses` e
JOIN `tmp_session_merge` t ON e.`expense_id` = t.`keep_id`
JOIN `schedules` s ON s.`schedule_id` = t.`schedule_id`
SET e.`category` = 'SESSION',
    e.`amount` = t.`total`,
    e.`description` = LEFT(CONCAT('Tiền sân + cầu ', s.`title`, ' (', DATE_FORMAT(s.`play_date`, '%d/%m/%Y'), ')'), 255),
    e.`note` = LEFT(CONCAT_WS(' | ', NULLIF(e.`note`, ''), t.`detail`), 255);

DELETE e FROM `expenses` e
JOIN `tmp_session_merge` t ON e.`schedule_id` = t.`schedule_id`
WHERE e.`category` IN ('COURT_RENT', 'SHUTTLECOCK') AND e.`expense_id` <> t.`keep_id`;

UPDATE `expenses` SET `category` = 'OTHER' WHERE `category` IN ('COURT_RENT', 'SHUTTLECOCK', 'EQUIPMENT');

DROP TEMPORARY TABLE `tmp_session_merge`;

COMMIT;

ALTER TABLE `expenses`
  MODIFY `category` enum('SESSION','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER';
