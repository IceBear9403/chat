-- =============================================================
-- 阶段八：邮箱注册/登录（app.auth.mode=email 时生效）
-- 执行方式：在远程库 social 上执行一次即可（幂等，重复执行无副作用）
--   mysql -h<host> -P<port> -uroot -p social < 本文件
--
-- 变更清单：
--   1. user.phone   允许为空（邮箱模式用户无手机号）
--   2. user.email   加宽（AES-GCM 加密存储，原 64 不足）
--   3. user         新增 email_hash（SHA-256，邮箱登录/查重/搜索精确匹配）+ 唯一索引
--   说明：老用户 email_hash 无需回填（历史无邮箱）；手机号验证相关代码保留，
--         切换 app.auth.mode 即可在手机/邮箱验证之间切换。
-- =============================================================

USE `social`;

-- ---------- 1. user.phone 允许为空 ----------
SET @col_null := (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'phone'
);
SET @ddl := IF(
    @col_null IS NOT NULL AND @col_null = 'NO',
    'ALTER TABLE user MODIFY COLUMN phone VARCHAR(255) DEFAULT NULL COMMENT ''手机号（AES-GCM 加密存储）''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. user.email 加宽 ----------
SET @col_type := (
    SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'email'
);
SET @ddl := IF(
    @col_type IS NOT NULL AND @col_type < 255,
    'ALTER TABLE user MODIFY COLUMN email VARCHAR(255) DEFAULT NULL COMMENT ''邮箱（AES-GCM 加密存储）''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. user.email_hash 列 + 唯一索引 ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user' AND COLUMN_NAME = 'email_hash'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE user ADD COLUMN email_hash CHAR(64) DEFAULT NULL COMMENT ''邮箱 SHA-256'' AFTER email',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_email_hash'
);
SET @ddl := IF(
    @idx_exists = 0,
    'ALTER TABLE user ADD UNIQUE KEY uk_email_hash (email_hash)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
