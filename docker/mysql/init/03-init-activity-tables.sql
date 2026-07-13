-- ============================================================
-- Activity module tables for wuduo_bank_activity database
-- ============================================================

USE wuduo_bank_activity;

-- Activity user progress tracking table
CREATE TABLE IF NOT EXISTS activity_user_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0 COMMENT 'All-time accumulated event count',
    period_key VARCHAR(16) NOT NULL COMMENT 'Period identifier, e.g. 2026-07',
    completion_count INT NOT NULL DEFAULT 0 COMMENT 'Completions awarded this period',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_activity_user_period (activity_id, user_id, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User activity progress tracking';

-- Activity version table (draft + version dual-track management)
CREATE TABLE IF NOT EXISTS activity_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL COMMENT 'FK to activity.id',
    version INT NOT NULL DEFAULT 1 COMMENT 'Sequential version number within an activity',
    content TEXT NOT NULL COMMENT 'Full activity config snapshot (JSON)',
    audit_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=draft, 1=pending audit, 2=approved, 3=rejected',
    is_online TINYINT NOT NULL DEFAULT 0 COMMENT '0=not live, 1=currently served to users',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT NULL,
    INDEX idx_activity_audit (activity_id, audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Activity version snapshots';

-- Add online_version_id to activity table (conditional for re-execution)
SET @dbname = DATABASE();
SET @tablename = 'activity';
SET @col_online_ver = 'online_version_id';

SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @col_online_ver) = 0,
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @col_online_ver, ' BIGINT DEFAULT NULL COMMENT ''Currently online version id (FK to activity_version.id)'' AFTER updated_by'),
    'SELECT ''Column online_version_id already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add idempotency and period tracking columns to activity_participation
-- Using conditional DDL to allow re-execution
SET @tablename = 'activity_participation';
SET @column1 = 'biz_no';
SET @column2 = 'period_key';
SET @indexname = 'idx_activity_user_bizno';

-- Only add biz_no column if it doesn't exist
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @column1) = 0,
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @column1, ' VARCHAR(64) DEFAULT NULL COMMENT ''Business number for idempotency'' AFTER remark'),
    'SELECT ''Column biz_no already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Only add period_key column if it doesn't exist
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @column2) = 0,
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @column2, ' VARCHAR(16) DEFAULT NULL COMMENT ''Period key, e.g. 2026-07'' AFTER ', @column1),
    'SELECT ''Column period_key already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Only add index if it doesn't exist
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) = 0,
    CONCAT('ALTER TABLE ', @tablename, ' ADD INDEX ', @indexname, ' (activity_id, user_id, biz_no)'),
    'SELECT ''Index already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
