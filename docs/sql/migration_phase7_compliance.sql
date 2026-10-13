-- =============================================================
-- 阶段七：合规与运维（内容审核 / 实名认证 / 用户封禁 / 操作日志 / 举报）
-- 执行方式：在远程库 social 上执行一次即可（幂等，重复执行无副作用）
--   mysql -h<host> -P<port> -uroot -p social < 本文件
--
-- 变更清单：
--   1. user         加 phone_hash（SHA-256，登录/查重/搜索用）、is_admin；phone 加宽容纳 ENC: 前缀密文
--   2. user_profile 加 real_name / id_card（AES-GCM 加密）、realname_status
--   3. 新建 user_log（操作日志，留存 180 天由定时任务清理）
--   4. 新建 report（动态举报）
--   注意：老用户 phone_hash 由应用启动时自动回填（LegacyPhoneBackfillRunner），
--         迁移只建列，不阻塞升级。
-- =============================================================

USE `social`;

-- ---------- 1. user：phone_hash ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'phone_hash'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE user ADD COLUMN phone_hash CHAR(64) DEFAULT NULL COMMENT ''手机号 SHA-256'' AFTER phone',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 1. user：phone 加宽（加密值含 ENC: 前缀，原 20 长度不足） ----------
SET @col_type := (
    SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'phone'
);
SET @ddl := IF(
    @col_type IS NOT NULL AND @col_type < 255,
    'ALTER TABLE user MODIFY COLUMN phone VARCHAR(255) NOT NULL COMMENT ''手机号（AES-GCM 加密存储）''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 1. user：is_admin ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'is_admin'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE user ADD COLUMN is_admin TINYINT NOT NULL DEFAULT 0 COMMENT ''是否管理员：0否 1是'' AFTER status',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 1. user：唯一索引收敛到 phone_hash，原 uk_phone 降级为普通索引 ----------
SET @uk_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_phone'
);
SET @ddl := IF(
    @uk_exists > 0,
    'ALTER TABLE user DROP INDEX uk_phone, ADD INDEX idx_phone (phone(20))',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @uk_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_phone_hash'
);
SET @ddl := IF(
    @uk_exists = 0,
    'ALTER TABLE user ADD UNIQUE KEY uk_phone_hash (phone_hash)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. user_profile：real_name / id_card / realname_status ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user_profile' AND COLUMN_NAME = 'real_name'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE user_profile ADD COLUMN real_name VARCHAR(64) DEFAULT NULL COMMENT ''真实姓名（AES-GCM 加密）'' AFTER region',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user_profile' AND COLUMN_NAME = 'id_card'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE user_profile ADD COLUMN id_card VARCHAR(128) DEFAULT NULL COMMENT ''身份证号（AES-GCM 加密）'' AFTER real_name',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user_profile' AND COLUMN_NAME = 'realname_status'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE user_profile ADD COLUMN realname_status TINYINT NOT NULL DEFAULT 0 COMMENT ''实名状态：0未实名 1已实名'' AFTER id_card',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. user_log ----------
CREATE TABLE IF NOT EXISTS `user_log` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '操作用户ID',
    `action`     VARCHAR(32)     NOT NULL COMMENT '动作：register/login/logout/realname/deleteAccount/ban/audit/report...',
    `detail`     VARCHAR(255)    DEFAULT NULL COMMENT '详情',
    `ip`         VARCHAR(64)     DEFAULT NULL COMMENT '客户端IP（X-Forwarded-For 首段）',
    `user_agent` VARCHAR(512)    DEFAULT NULL COMMENT 'User-Agent',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `created_at`),
    KEY `idx_action_time` (`action`, `created_at`)
) ENGINE = InnoDB COMMENT = '用户操作日志';

-- ---------- 4. report ----------
CREATE TABLE IF NOT EXISTS `report` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `moment_id`     BIGINT UNSIGNED NOT NULL COMMENT '被举报动态ID',
    `reporter_id`   BIGINT UNSIGNED NOT NULL COMMENT '举报人ID',
    `reason`        VARCHAR(200)    DEFAULT NULL COMMENT '举报理由',
    `handle_status` TINYINT         NOT NULL DEFAULT 0 COMMENT '处理状态：0待处理 1已处理',
    `handled_by`    BIGINT UNSIGNED DEFAULT NULL COMMENT '处理人（管理员）ID',
    `handled_at`    DATETIME        DEFAULT NULL COMMENT '处理时间',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_moment_id` (`moment_id`),
    KEY `idx_status_time` (`handle_status`, `created_at`)
) ENGINE = InnoDB COMMENT = '动态举报';

-- =============================================================
-- 完成。老用户 phone_hash 回填见应用启动器 LegacyPhoneBackfillRunner。
-- =============================================================
