-- =============================================================
-- 阶段九：昵称管理（修改间隔限制）
-- 执行方式：在远程库 social 上执行一次即可（幂等，重复执行无副作用）
--   mysql -h<host> -P<port> -uroot -p social < 本文件
--
-- 变更清单：
--   1. user_profile 新增 nickname_updated_at（昵称最近一次自行修改时间）
--      用于 app.nickname.change-interval-days 的修改间隔限制；
--      管理员改昵称 / 定时任务改名不写入该字段，不影响用户后续自行修改。
-- =============================================================

USE `social`;

-- ---------- 1. user_profile.nickname_updated_at 列（幂等） ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'user_profile' AND COLUMN_NAME = 'nickname_updated_at'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE user_profile ADD COLUMN nickname_updated_at DATETIME DEFAULT NULL COMMENT ''昵称最近一次自行修改时间'' AFTER region',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
