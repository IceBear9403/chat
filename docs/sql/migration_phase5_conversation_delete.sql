-- =============================================================
-- 阶段五：会话滑动删除 —— conversation 表软删字段
-- 执行方式：在远程库 social 上执行一次即可（幂等，重复执行无副作用）
--   mysql -h8.142.176.238 -P13306 -uroot -p social < 本文件
-- =============================================================

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'conversation'
      AND COLUMN_NAME = 'deleted_a'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE conversation ADD COLUMN deleted_a DATETIME DEFAULT NULL COMMENT ''A方删除时间, NULL=未删''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'social' AND TABLE_NAME = 'conversation'
      AND COLUMN_NAME = 'deleted_b'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE conversation ADD COLUMN deleted_b DATETIME DEFAULT NULL COMMENT ''B方删除时间, NULL=未删''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
