-- Đợt thu (payment_batches): gom các khoản thu tạo cùng lúc để xem / thu / sửa / xóa theo đợt.
-- Phí tháng: mỗi tháng 1 đợt (unique payment_type + year + month). Thu thêm: mỗi lần tạo 1 đợt.
-- Nội dung khoản thu chuyển lên đợt (bỏ cột payments.description); payments giữ payment_type/month/year
-- để unique uk_payment_user_month tiếp tục chặn thu trùng tháng ở tầng DB.
-- Chạy 1 lần trên DB đã có schema cũ; dữ liệu cũ được gán vào đợt (thu thêm: cùng nội dung + số tiền + ngày tạo + người tạo).

START TRANSACTION;

CREATE TABLE `payment_batches` (
  `batch_id` bigint NOT NULL AUTO_INCREMENT,
  `payment_type` enum('MONTHLY','EXTRA') COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `month` tinyint DEFAULT NULL,
  `year` smallint DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL COMMENT 'Thu thêm: số tiền mỗi người; phí tháng: NULL (theo giới tính)',
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_batch_period` (`payment_type`,`year`,`month`),
  KEY `fk_batches_creator` (`created_by`),
  CONSTRAINT `fk_batches_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_batch_month` CHECK (((`month` is null) or (`month` between 1 and 12))),
  CONSTRAINT `chk_batch_type` CHECK (((`payment_type` = _utf8mb4'MONTHLY' and `month` is not null and `year` is not null)
      or (`payment_type` = _utf8mb4'EXTRA' and `month` is null and `year` is null and `amount` is not null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `payments` ADD COLUMN `batch_id` bigint NULL AFTER `payment_id`;

-- Phí tháng cũ: 1 đợt / tháng
INSERT INTO `payment_batches` (`payment_type`, `description`, `month`, `year`, `amount`, `created_by`, `created_at`)
SELECT 'MONTHLY', CONCAT('Phí tháng ', `month`, '/', `year`), `month`, `year`, NULL, MIN(`created_by`), MIN(`created_at`)
FROM `payments` WHERE `payment_type` = 'MONTHLY'
GROUP BY `year`, `month`;

UPDATE `payments` p
JOIN `payment_batches` b ON b.`payment_type` = 'MONTHLY' AND b.`year` = p.`year` AND b.`month` = p.`month`
SET p.`batch_id` = b.`batch_id`
WHERE p.`payment_type` = 'MONTHLY';

-- Thu thêm cũ: gom theo nội dung + số tiền + ngày tạo + người tạo
INSERT INTO `payment_batches` (`payment_type`, `description`, `amount`, `created_by`, `created_at`)
SELECT 'EXTRA', `description`, `amount`, `created_by`, MIN(`created_at`)
FROM `payments` WHERE `payment_type` = 'EXTRA'
GROUP BY `description`, `amount`, DATE(`created_at`), `created_by`;

UPDATE `payments` p
JOIN `payment_batches` b ON b.`payment_type` = 'EXTRA' AND b.`description` = p.`description`
    AND b.`amount` = p.`amount` AND DATE(b.`created_at`) = DATE(p.`created_at`) AND b.`created_by` = p.`created_by`
SET p.`batch_id` = b.`batch_id`
WHERE p.`payment_type` = 'EXTRA';

ALTER TABLE `payments`
  MODIFY `batch_id` bigint NOT NULL,
  ADD KEY `fk_payments_batch` (`batch_id`),
  ADD CONSTRAINT `fk_payments_batch` FOREIGN KEY (`batch_id`) REFERENCES `payment_batches` (`batch_id`),
  DROP COLUMN `description`;

COMMIT;
