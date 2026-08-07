-- =============================================================
-- H5 社交网站 数据库建表脚本
-- 数据库：MySQL 8.x，引擎 InnoDB，字符集 utf8mb4
-- 说明：阶段一先建全量核心表；朋友圈/聊天/通话的实体代码在对应阶段补齐
-- 执行：mysql -uroot -p < schema.sql
-- =============================================================

CREATE DATABASE IF NOT EXISTS `social` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `social`;

-- -------------------------------------------------------------
-- 用户账号表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `phone`      VARCHAR(20)     NOT NULL COMMENT '手机号（登录主凭证）',
    `email`      VARCHAR(64)     DEFAULT NULL COMMENT '邮箱（可选）',
    `password`   VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希密码',
    `status`     TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0正常 1封禁 2注销',
    `deleted`    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_email` (`email`)
) ENGINE = InnoDB COMMENT = '用户账号';

-- -------------------------------------------------------------
-- 用户资料表（1:1）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_profile` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `nickname`   VARCHAR(20)     NOT NULL COMMENT '昵称',
    `avatar`     VARCHAR(255)    DEFAULT NULL COMMENT '头像 OSS Key/URL',
    `signature`  VARCHAR(100)    DEFAULT NULL COMMENT '个性签名',
    `gender`     TINYINT         NOT NULL DEFAULT 0 COMMENT '性别：0未知 1男 2女',
    `region`     VARCHAR(50)     DEFAULT NULL COMMENT '地区',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB COMMENT = '用户资料';

-- -------------------------------------------------------------
-- 用户关系表（好友 / 关注）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_relation` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '主动方',
    `target_user_id` BIGINT UNSIGNED NOT NULL COMMENT '目标方',
    `relation_type`  TINYINT         NOT NULL COMMENT '类型：1好友申请 2关注',
    `status`         TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1已同意 2已拒绝 3已解除',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_target_type` (`user_id`, `target_user_id`, `relation_type`),
    KEY `idx_target_user` (`target_user_id`)
) ENGINE = InnoDB COMMENT = '用户关系';

-- -------------------------------------------------------------
-- 隐私设置表（每用户一行）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `feed_rule` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`               BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `moments_visible`       TINYINT         NOT NULL DEFAULT 0 COMMENT '朋友圈可见：0公开 1仅好友 2私密',
    `allow_stranger_reply`  TINYINT         NOT NULL DEFAULT 0 COMMENT '允许陌生人评论：0否 1是',
    `chat_policy`           TINYINT         NOT NULL DEFAULT 0 COMMENT '私聊策略：0所有人 1仅好友 2不允许',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB COMMENT = '隐私设置';

-- -------------------------------------------------------------
-- 朋友圈动态
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `moment` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '发布者',
    `content`       TEXT COMMENT '正文（纯文本，图片见 moment_media）',
    `type`          TINYINT         NOT NULL DEFAULT 0 COMMENT '类型：0图文 1纯文字 2转发',
    `visibility`    TINYINT         NOT NULL DEFAULT 0 COMMENT '可见范围：0公开 1仅好友 2私密',
    `repost_from`   BIGINT UNSIGNED DEFAULT NULL COMMENT '转发来源动态ID',
    `status`        TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1已发布 2审核不通过 3已删除',
    `like_count`    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '评论数',
    `repost_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '转发数',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `created_at`),
    KEY `idx_status_time` (`status`, `created_at`)
) ENGINE = InnoDB COMMENT = '朋友圈动态';

-- -------------------------------------------------------------
-- 动态媒体（图片/视频）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `moment_media` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `moment_id`     BIGINT UNSIGNED NOT NULL COMMENT '动态ID',
    `media_type`    TINYINT         NOT NULL COMMENT '类型：1图片 2视频',
    `url`           VARCHAR(255)    NOT NULL COMMENT '资源地址（OSS Key）',
    `thumbnail_url` VARCHAR(255)    DEFAULT NULL COMMENT '缩略图地址',
    `width`         INT             DEFAULT NULL,
    `height`        INT             DEFAULT NULL,
    `sort`          INT             NOT NULL DEFAULT 0 COMMENT '排序，从0开始',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_moment_id` (`moment_id`)
) ENGINE = InnoDB COMMENT = '动态媒体';

-- -------------------------------------------------------------
-- 动态点赞（幂等：unique 约束防重复点赞）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `moment_like` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `moment_id`  BIGINT UNSIGNED NOT NULL,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_moment_user` (`moment_id`, `user_id`)
) ENGINE = InnoDB COMMENT = '动态点赞';

-- -------------------------------------------------------------
-- 动态评论（parent_id 支持楼中楼）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `moment_comment` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `moment_id`  BIGINT UNSIGNED NOT NULL,
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '评论人',
    `parent_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '被回复评论ID（NULL为顶级评论）',
    `reply_to`   BIGINT UNSIGNED DEFAULT NULL COMMENT '被回复用户ID',
    `content`    VARCHAR(500)    NOT NULL,
    `status`     TINYINT         NOT NULL DEFAULT 0 COMMENT '0正常 1已删除',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_moment_id` (`moment_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB COMMENT = '动态评论';

-- -------------------------------------------------------------
-- 动态转发
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `moment_repost` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `moment_id`   BIGINT UNSIGNED NOT NULL COMMENT '被转发动态ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '转发人',
    `content`     VARCHAR(200)    DEFAULT NULL COMMENT '转发附带文字',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_moment_user` (`moment_id`, `user_id`),
    KEY `idx_user_time` (`user_id`, `created_at`)
) ENGINE = InnoDB COMMENT = '动态转发';

-- -------------------------------------------------------------
-- 会话（单聊：一条会话对应一对用户，用 user_a < user_b 规范排序）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `conversation` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_a`             BIGINT UNSIGNED NOT NULL COMMENT '较小用户ID',
    `user_b`             BIGINT UNSIGNED NOT NULL COMMENT '较大用户ID',
    `last_message_id`    BIGINT UNSIGNED DEFAULT NULL,
    `last_message_time`  DATETIME        DEFAULT NULL,
    `last_message_preview` VARCHAR(100)  DEFAULT NULL COMMENT '会话列表预览',
    `deleted_a`          DATETIME        DEFAULT NULL COMMENT 'A方删除时间, NULL=未删;对方发新消息后重新可见',
    `deleted_b`          DATETIME        DEFAULT NULL COMMENT 'B方删除时间, NULL=未删;对方发新消息后重新可见',
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_pair` (`user_a`, `user_b`),
    KEY `idx_last_time` (`last_message_time`)
) ENGINE = InnoDB COMMENT = '会话';

-- -------------------------------------------------------------
-- 消息
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `message` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT UNSIGNED NOT NULL,
    `sender_id`       BIGINT UNSIGNED NOT NULL,
    `receiver_id`     BIGINT UNSIGNED NOT NULL,
    `msg_type`        TINYINT         NOT NULL COMMENT '类型：1文本 2图片 3语音 4视频 5通话',
    `content`         VARCHAR(2000)   NOT NULL COMMENT '文本内容 / 资源Key',
    `client_msg_id`   VARCHAR(64)     DEFAULT NULL COMMENT '客户端生成幂等ID',
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '0已发送 1已读',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conversation_time` (`conversation_id`, `created_at`),
    KEY `idx_receiver_status` (`receiver_id`, `status`),
    UNIQUE KEY `uk_client_msg` (`sender_id`, `client_msg_id`)
) ENGINE = InnoDB COMMENT = '消息';

-- -------------------------------------------------------------
-- 通话记录
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `call_record` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `call_id`    VARCHAR(64)     NOT NULL COMMENT '信令全局呼叫ID',
    `caller_id`  BIGINT UNSIGNED NOT NULL COMMENT '主叫',
    `callee_id`  BIGINT UNSIGNED NOT NULL COMMENT '被叫',
    `call_type`  TINYINT         NOT NULL COMMENT '1语音 2视频',
    `status`     TINYINT         NOT NULL COMMENT '0呼叫中 1已接通 2已拒绝 3未接 4挂断 5掉线',
    `duration`   INT             NOT NULL DEFAULT 0 COMMENT '通话时长（秒）',
    `started_at` DATETIME        DEFAULT NULL COMMENT '接通时间',
    `ended_at`   DATETIME        DEFAULT NULL,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_call_id` (`call_id`),
    KEY `idx_callee_time` (`callee_id`, `created_at`)
) ENGINE = InnoDB COMMENT = '通话记录';

-- =============================================================
-- 索引设计说明
-- -------------------------------------------------------------
-- user.phone           唯一索引：登录 / 查重，高频
-- user_relation        唯一(主动,目标,类型)：好友/关注幂等
-- moment(status,time)  信息流拉取按可见性+时间，推荐流热度排序（阶段二）
-- moment_like          唯一(moment,user)：点赞幂等
-- message(conv,time)   会话内按时间分页拉历史
-- call_record.call_id  唯一：信令去重
-- =============================================================
