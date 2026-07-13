-- ============================================
-- 积分系统 (Point System) 数据库表 DDL
-- ============================================

USE `wuduo_bank_point`;

-- 积分账户表
DROP TABLE IF EXISTS `point_account`;
CREATE TABLE `point_account` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `user_id`         BIGINT       NOT NULL COMMENT 'User ID',
    `total_earned`    BIGINT       NOT NULL DEFAULT 0 COMMENT 'Total earned points',
    `total_used`      BIGINT       NOT NULL DEFAULT 0 COMMENT 'Total used points',
    `total_expired`   BIGINT       NOT NULL DEFAULT 0 COMMENT 'Total expired points',
    `frozen`          BIGINT       NOT NULL DEFAULT 0 COMMENT 'Frozen points',
    `available`       BIGINT       NOT NULL DEFAULT 0 COMMENT 'Available points',
    `version`         INT          NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Point account table';

-- 积分流水表
DROP TABLE IF EXISTS `point_record`;
CREATE TABLE `point_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `record_no`       VARCHAR(64)  NOT NULL COMMENT 'Record number (unique)',
    `user_id`         BIGINT       NOT NULL COMMENT 'User ID',
    `point_amount`    BIGINT       NOT NULL COMMENT 'Point amount (positive=earn, negative=deduct)',
    `type`            INT          NOT NULL COMMENT 'Record type',
    `biz_source`      VARCHAR(64)  DEFAULT NULL COMMENT 'Business source',
    `biz_no`          VARCHAR(64)  DEFAULT NULL COMMENT 'Business number (idempotent key)',
    `expire_time`     DATETIME     DEFAULT NULL COMMENT 'Expire time',
    `status`          INT          NOT NULL DEFAULT 1 COMMENT 'Record status',
    `used_amount`     BIGINT       NOT NULL DEFAULT 0 COMMENT 'Already deducted amount (for FIFO)',
    `remark`          VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_no` (`record_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_status_expire` (`user_id`, `status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Point record (transaction log) table';

-- 积分冻结表
DROP TABLE IF EXISTS `point_freeze`;
CREATE TABLE `point_freeze` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `freeze_no`       VARCHAR(64)  NOT NULL COMMENT 'Freeze number (unique)',
    `user_id`         BIGINT       NOT NULL COMMENT 'User ID',
    `freeze_amount`   BIGINT       NOT NULL COMMENT 'Freeze amount',
    `biz_no`          VARCHAR(64)  NOT NULL COMMENT 'Business number (idempotent key)',
    `status`          INT          NOT NULL DEFAULT 0 COMMENT 'Freeze status: 0=frozen, 1=unfrozen, 2=deducted',
    `remark`          VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_freeze_no` (`freeze_no`),
    UNIQUE KEY `uk_biz_no` (`biz_no`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Point freeze table';
