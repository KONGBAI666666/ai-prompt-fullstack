-- =============================================
-- AI Prompt 管理系统 数据库初始化脚本
-- 使用方法：Navicat 连接本地 MySQL 后直接运行整个文件
-- =============================================
CREATE DATABASE IF NOT EXISTS ai_prompt_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ai_prompt_db;

-- 1. 用户表（表名 sys_user，避免 user 关键字冲突）
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL COMMENT '用户名',
    password    VARCHAR(255) NOT NULL COMMENT '密码（BCrypt密文）',
    email       VARCHAR(100)          COMMENT '邮箱',
    avatar      VARCHAR(255)          COMMENT '头像地址',
    role        VARCHAR(20)  DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
    status      INT          DEFAULT 1 COMMENT '状态：1正常 0禁用',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_username (username)
) COMMENT '用户表';

-- 2. Prompt表
DROP TABLE IF EXISTS prompt;
CREATE TABLE prompt (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    title          VARCHAR(100) NOT NULL COMMENT 'Prompt标题',
    content        TEXT         NOT NULL COMMENT 'Prompt正文',
    description    VARCHAR(500)          COMMENT '描述',
    category_id    BIGINT                COMMENT '分类id',
    user_id        BIGINT                COMMENT '创建者用户id',
    view_count     INT DEFAULT 0         COMMENT '浏览次数',
    favorite_count INT DEFAULT 0         COMMENT '收藏次数',
    status         INT DEFAULT 1         COMMENT '状态：1正常（预留）',
    create_time    DATETIME COMMENT '创建时间',
    update_time    DATETIME COMMENT '更新时间',
    KEY idx_category_id (category_id),
    KEY idx_user_id (user_id)
) COMMENT 'Prompt表';

-- 3. 分类表
DROP TABLE IF EXISTS category;
CREATE TABLE category (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(50)  NOT NULL COMMENT '分类名称',
    description VARCHAR(200) COMMENT '分类描述',
    create_time DATETIME     COMMENT '创建时间',
    UNIQUE KEY uk_category_name (name)
) COMMENT 'Prompt分类表';

-- 4. 收藏表
DROP TABLE IF EXISTS favorite;
CREATE TABLE favorite (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT COMMENT '收藏人用户id',
    prompt_id   BIGINT COMMENT '被收藏的Prompt id',
    create_time DATETIME COMMENT '收藏时间',
    UNIQUE KEY uk_user_prompt (user_id, prompt_id)
) COMMENT '收藏表';

-- 5. 使用记录表
DROP TABLE IF EXISTS prompt_history;
CREATE TABLE prompt_history (
    id        BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id   BIGINT COMMENT '使用人用户id',
    prompt_id BIGINT COMMENT '使用的Prompt id',
    use_time  DATETIME COMMENT '使用时间',
    KEY idx_user_id (user_id)
) COMMENT 'Prompt使用记录表';

-- =============================================
-- 数据库对象：补充索引、视图、触发器、存储过程
-- =============================================

-- 补充索引：Prompt 列表按创建时间倒序展示，加速排序
CREATE INDEX idx_create_time ON prompt (create_time);

-- 补充索引：按 Prompt 维度查收藏/使用记录（级联清理、热度统计）
CREATE INDEX idx_prompt_id ON favorite (prompt_id);
CREATE INDEX idx_prompt_id ON prompt_history (prompt_id);

-- 视图1：Prompt 详情视图（关联分类名与作者名，简化多表查询）
CREATE OR REPLACE VIEW v_prompt_detail AS
SELECT p.id, p.title, p.description, c.name AS category_name,
       u.username AS author_name, p.view_count, p.favorite_count, p.create_time
FROM prompt p
LEFT JOIN category c ON p.category_id = c.id
LEFT JOIN sys_user u ON p.user_id = u.id;

-- 视图2：用户发布统计视图（每个用户的发布数、总浏览量、总收藏量）
CREATE OR REPLACE VIEW v_user_stats AS
SELECT u.id AS user_id, u.username,
       COUNT(p.id) AS prompt_count,
       IFNULL(SUM(p.view_count), 0) AS total_view,
       IFNULL(SUM(p.favorite_count), 0) AS total_favorite
FROM sys_user u
LEFT JOIN prompt p ON p.user_id = u.id
GROUP BY u.id, u.username;

-- 触发器：删除 Prompt 时级联清理收藏记录与使用记录，数据库层防止孤儿数据
-- （与应用层事务级联删除双重保障，重复删除为幂等操作，互不冲突）
DROP TRIGGER IF EXISTS trg_prompt_after_delete;
DELIMITER $$
CREATE TRIGGER trg_prompt_after_delete
AFTER DELETE ON prompt
FOR EACH ROW
BEGIN
    DELETE FROM favorite WHERE prompt_id = OLD.id;
    DELETE FROM prompt_history WHERE prompt_id = OLD.id;
END$$
DELIMITER ;

-- 存储过程：系统数据统计（用户数、Prompt 数、收藏数、今日新增 Prompt 数）
DROP PROCEDURE IF EXISTS sp_system_stats;
DELIMITER $$
CREATE PROCEDURE sp_system_stats()
BEGIN
    SELECT (SELECT COUNT(*) FROM sys_user)  AS user_count,
           (SELECT COUNT(*) FROM prompt)    AS prompt_count,
           (SELECT COUNT(*) FROM favorite)  AS favorite_count,
           (SELECT COUNT(*) FROM prompt WHERE DATE(create_time) = CURDATE()) AS today_prompt_count;
END$$
DELIMITER ;

-- =============================================
-- 初始化数据
-- =============================================

-- 默认账号：admin/admin123（管理员）、test/123456（普通用户）
INSERT INTO sys_user (username, password, email, role, status, create_time, update_time) VALUES
('admin', '$2a$10$xh1gtXaIbBkQhrTOrqjO1.Q.z2a.fQhUSg2.utCFilIpyynfnL.Em', 'admin@example.com', 'ADMIN', 1, NOW(), NOW()),
('test',  '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS',  'test@example.com',  'USER',  1, NOW(), NOW());

-- 6 条默认分类
INSERT INTO category (name, description, create_time) VALUES
('编程', '编程开发相关的Prompt', NOW()),
('学习', '学习提效相关的Prompt', NOW()),
('写作', '文章写作相关的Prompt', NOW()),
('办公', '日常办公相关的Prompt', NOW()),
('翻译', '语言翻译相关的Prompt', NOW()),
('营销', '市场营销相关的Prompt', NOW());

-- 3 条不同分类的示例 Prompt（作者为 admin，id=1）
INSERT INTO prompt (title, content, description, category_id, user_id, view_count, favorite_count, status, create_time, update_time) VALUES
('Java代码优化助手', '你是一名Java专家，请优化以下代码，指出性能问题和改进建议：', '用于Java代码优化', 1, 1, 0, 0, 1, NOW(), NOW()),
('英语学习计划制定', '你是一名英语老师，请根据我的水平制定一份30天英语学习计划，我的水平是：', '制定个性化英语学习计划', 2, 1, 0, 0, 1, NOW(), NOW()),
('公众号文章润色', '你是一名资深编辑，请帮我润色以下公众号文章，使其更有吸引力：', '公众号文章润色使用', 3, 1, 0, 0, 1, NOW(), NOW());
