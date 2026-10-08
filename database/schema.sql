-- MySQL dump 10.13  Distrib 9.7.0, for Win64 (x86_64)
--
-- Host: localhost    Database: badminton_club_plus
-- ------------------------------------------------------
-- Server version	9.7.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `attendances`
--

DROP TABLE IF EXISTS `attendances`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attendances` (
  `attendance_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `schedule_id` bigint NOT NULL,
  `status` enum('NOT_MARKED','PRESENT','ABSENT') COLLATE utf8mb4_unicode_ci DEFAULT 'NOT_MARKED',
  `checked_at` datetime DEFAULT NULL,
  `checked_by` bigint DEFAULT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`attendance_id`),
  UNIQUE KEY `uk_attendance_user_schedule` (`user_id`,`schedule_id`),
  KEY `fk_attendances_schedule` (`schedule_id`),
  KEY `fk_attendances_checker` (`checked_by`),
  CONSTRAINT `fk_attendances_checker` FOREIGN KEY (`checked_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_attendances_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `schedules` (`schedule_id`),
  CONSTRAINT `fk_attendances_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `courts`
--

DROP TABLE IF EXISTS `courts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `courts` (
  `court_id` bigint NOT NULL AUTO_INCREMENT,
  `court_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `hourly_rate` decimal(12,2) NOT NULL,
  `phone` varchar(15) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active` tinyint(1) DEFAULT '1',
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`court_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `expenses`
--

DROP TABLE IF EXISTS `expenses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `expenses` (
  `expense_id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` enum('COURT_RENT','SHUTTLECOCK','EQUIPMENT','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER',
  `schedule_id` bigint DEFAULT NULL,
  `amount` decimal(12,2) NOT NULL,
  `expense_date` date NOT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`expense_id`),
  KEY `fk_expenses_creator` (`created_by`),
  KEY `fk_expenses_schedule` (`schedule_id`),
  CONSTRAINT `fk_expenses_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_expenses_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `schedules` (`schedule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `fee_settings`
--

DROP TABLE IF EXISTS `fee_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fee_settings` (
  `fee_setting_id` bigint NOT NULL AUTO_INCREMENT,
  `fee_type` enum('MONTHLY','GUEST') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MONTHLY',
  `gender` enum('MALE','FEMALE') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amount` decimal(12,2) NOT NULL,
  `effective_from` date NOT NULL,
  `active` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`fee_setting_id`),
  CONSTRAINT `chk_fee_gender` CHECK ((((`fee_type` = _utf8mb4'MONTHLY') and (`gender` is not null)) or ((`fee_type` = _utf8mb4'GUEST') and (`gender` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `guest_registrations`
--

DROP TABLE IF EXISTS `guest_registrations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `guest_registrations` (
  `guest_registration_id` bigint NOT NULL AUTO_INCREMENT,
  `guest_id` bigint NOT NULL,
  `schedule_id` bigint NOT NULL,
  `invited_by` bigint DEFAULT NULL,
  `fee` decimal(12,2) DEFAULT '0.00',
  `payment_status` enum('UNPAID','PAID') COLLATE utf8mb4_unicode_ci DEFAULT 'UNPAID',
  `paid_at` datetime DEFAULT NULL,
  `collected_by` bigint DEFAULT NULL,
  `attendance_status` enum('NOT_MARKED','PRESENT','ABSENT') COLLATE utf8mb4_unicode_ci DEFAULT 'NOT_MARKED',
  `registered_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`guest_registration_id`),
  UNIQUE KEY `uk_guest_schedule` (`guest_id`,`schedule_id`),
  KEY `fk_guest_registrations_schedule` (`schedule_id`),
  KEY `fk_guest_reg_inviter` (`invited_by`),
  KEY `fk_guest_reg_collector` (`collected_by`),
  CONSTRAINT `fk_guest_reg_collector` FOREIGN KEY (`collected_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_guest_reg_inviter` FOREIGN KEY (`invited_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_guest_registrations_guest` FOREIGN KEY (`guest_id`) REFERENCES `guests` (`guest_id`),
  CONSTRAINT `fk_guest_registrations_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `schedules` (`schedule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `guests`
--

DROP TABLE IF EXISTS `guests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `guests` (
  `guest_id` bigint NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(15) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gender` enum('MALE','FEMALE') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`guest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `levels`
--

DROP TABLE IF EXISTS `levels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `levels` (
  `level_id` bigint NOT NULL AUTO_INCREMENT,
  `level_name` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`level_id`),
  UNIQUE KEY `level_name` (`level_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment_batches`
--

DROP TABLE IF EXISTS `payment_batches`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_batches` (
  `batch_id` bigint NOT NULL AUTO_INCREMENT,
  `payment_type` enum('MONTHLY','EXTRA') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `month` tinyint DEFAULT NULL,
  `year` smallint DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL COMMENT 'Thu thêm: số tiền mỗi người; phí tháng: NULL (theo giới tính)',
  `note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_batch_period` (`payment_type`,`year`,`month`),
  KEY `fk_batches_creator` (`created_by`),
  CONSTRAINT `fk_batches_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_batch_month` CHECK (((`month` is null) or (`month` between 1 and 12))),
  CONSTRAINT `chk_batch_type` CHECK ((((`payment_type` = _utf8mb4'MONTHLY') and (`month` is not null) and (`year` is not null)) or ((`payment_type` = _utf8mb4'EXTRA') and (`month` is null) and (`year` is null) and (`amount` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payments`
--

DROP TABLE IF EXISTS `payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payments` (
  `payment_id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `payment_type` enum('MONTHLY','EXTRA') COLLATE utf8mb4_unicode_ci NOT NULL,
  `month` tinyint DEFAULT NULL,
  `year` smallint DEFAULT NULL,
  `amount` decimal(12,2) NOT NULL,
  `status` enum('UNPAID','PAID') COLLATE utf8mb4_unicode_ci DEFAULT 'UNPAID',
  `payment_method` enum('CASH','BANK_TRANSFER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`payment_id`),
  UNIQUE KEY `uk_payment_user_month` (`user_id`,`payment_type`,`year`,`month`),
  KEY `fk_payments_creator` (`created_by`),
  KEY `fk_payments_batch` (`batch_id`),
  CONSTRAINT `fk_payments_batch` FOREIGN KEY (`batch_id`) REFERENCES `payment_batches` (`batch_id`),
  CONSTRAINT `fk_payments_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_payments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_payment_month` CHECK (((`month` is null) or (`month` between 1 and 12))),
  CONSTRAINT `chk_payment_monthly` CHECK (((`payment_type` <> _utf8mb4'MONTHLY') or ((`month` is not null) and (`year` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `registrations`
--

DROP TABLE IF EXISTS `registrations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `registrations` (
  `registration_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `schedule_id` bigint NOT NULL,
  `registered_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `status` enum('REGISTERED','CANCELLED') COLLATE utf8mb4_unicode_ci DEFAULT 'REGISTERED',
  PRIMARY KEY (`registration_id`),
  UNIQUE KEY `uk_user_schedule` (`user_id`,`schedule_id`),
  KEY `fk_registrations_schedule` (`schedule_id`),
  CONSTRAINT `fk_registrations_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `schedules` (`schedule_id`),
  CONSTRAINT `fk_registrations_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `role_id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schedules`
--

DROP TABLE IF EXISTS `schedules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedules` (
  `schedule_id` bigint NOT NULL AUTO_INCREMENT,
  `court_id` bigint NOT NULL,
  `court_count` int NOT NULL DEFAULT '1',
  `title` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `schedule_type` enum('FIXED','EXTRA') COLLATE utf8mb4_unicode_ci NOT NULL,
  `play_date` date NOT NULL,
  `start_time` time NOT NULL,
  `end_time` time NOT NULL,
  `max_players` int NOT NULL,
  `status` enum('OPEN','CLOSED','CANCELLED','COMPLETED') COLLATE utf8mb4_unicode_ci DEFAULT 'OPEN',
  `created_by` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`schedule_id`),
  KEY `fk_schedules_court` (`court_id`),
  KEY `fk_schedules_creator` (`created_by`),
  KEY `idx_schedules_play_date` (`play_date`),
  CONSTRAINT `fk_schedules_court` FOREIGN KEY (`court_id`) REFERENCES `courts` (`court_id`),
  CONSTRAINT `fk_schedules_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `chk_schedule_time` CHECK ((`end_time` > `start_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(15) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gender` enum('MALE','FEMALE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `birth_date` date DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role_id` bigint NOT NULL,
  `level_id` bigint DEFAULT NULL,
  `join_date` date NOT NULL,
  `status` enum('ACTIVE','INACTIVE','LOCKED') COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `phone` (`phone`),
  KEY `fk_users_role` (`role_id`),
  KEY `fk_users_level` (`level_id`),
  CONSTRAINT `fk_users_level` FOREIGN KEY (`level_id`) REFERENCES `levels` (`level_id`),
  CONSTRAINT `fk_users_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'badminton_club_plus'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-08 20:55:16
