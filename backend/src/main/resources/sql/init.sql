-- =============================================
-- AI Prompt 管理系统 数据库初始化脚本
-- 使用方法：Navicat 连接本地 MySQL 后直接运行整个文件
-- 包含：9 张表、索引、初始化数据、
--       2 个视图、1 个触发器、1 个存储过程、RBAC 权限管理子系统（数据库课设配套）
-- 本脚本可重复执行（先删后建）
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

-- 6. 用户状态变更审计日志表（由触发器 trg_user_status_change 自动写入）
DROP TABLE IF EXISTS user_status_log;
CREATE TABLE user_status_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT      COMMENT '被操作的用户id',
    username    VARCHAR(50) COMMENT '用户名快照',
    old_status  INT         COMMENT '变更前状态：1正常 0禁用',
    new_status  INT         COMMENT '变更后状态：1正常 0禁用',
    change_time DATETIME    COMMENT '变更时间',
    KEY idx_log_user_id (user_id)
) COMMENT '用户状态变更审计日志表';

-- =============================================
-- 权限管理子系统（RBAC：用户分组、授权、权限维护）
-- 7-9. 角色 / 权限点 / 角色-权限关联 三张表
-- 说明：sys_user.role 仍作为用户的默认主角色；通过 role_permission 维护"角色→权限"
-- 的多对多关系，再由 StpInterfaceImpl 在登录态加载到 Sa-Token 上下文，
-- 实现"用户分组 → 角色分配 → 权限维护"的完整授权链。
-- =============================================

-- 7. 角色字典表
DROP TABLE IF EXISTS role;
CREATE TABLE role (
    code        VARCHAR(20)  PRIMARY KEY COMMENT '角色编码（主键），如 USER / ADMIN / SUPER_ADMIN',
    name        VARCHAR(50)  NOT NULL COMMENT '角色名称',
    description VARCHAR(200)             COMMENT '角色描述',
    create_time DATETIME                 COMMENT '创建时间'
) COMMENT '角色字典表（用户分组的载体）';

-- 8. 权限点字典表
DROP TABLE IF EXISTS permission;
CREATE TABLE permission (
    code        VARCHAR(50)  PRIMARY KEY COMMENT '权限点编码（主键），如 prompt:create / user:manage',
    name        VARCHAR(50)  NOT NULL COMMENT '权限点名称',
    module      VARCHAR(30)             COMMENT '所属模块',
    description VARCHAR(200)            COMMENT '权限点描述'
) COMMENT '权限点字典表（系统所有可授权的动作）';

-- 9. 角色-权限关联表（M:N）
DROP TABLE IF EXISTS role_permission;
CREATE TABLE role_permission (
    role_code       VARCHAR(20) COMMENT '角色编码',
    permission_code VARCHAR(50) COMMENT '权限点编码',
    PRIMARY KEY (role_code, permission_code),
    KEY idx_rp_perm (permission_code)
) COMMENT '角色-权限关联表';

-- =============================================
-- 补充索引
-- =============================================

-- Prompt 列表按创建时间倒序展示，加速排序
CREATE INDEX idx_create_time ON prompt (create_time);

-- 按 Prompt 维度查收藏/使用记录（应用层级联删除、热度统计）
CREATE INDEX idx_prompt_id ON favorite (prompt_id);
CREATE INDEX idx_prompt_id ON prompt_history (prompt_id);

-- =============================================
-- 初始化数据（每张表不低于10条记录）
-- =============================================

-- 默认账号：admin/admin123（管理员）、test/123456（普通用户），其余用户密码均为123456
INSERT INTO sys_user (username, password, email, role, status, create_time, update_time) VALUES
('admin',    '$2a$10$xh1gtXaIbBkQhrTOrqjO1.Q.z2a.fQhUSg2.utCFilIpyynfnL.Em', 'admin@example.com',    'ADMIN', 1, NOW(), NOW()),
('test',     '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS',  'test@example.com',     'USER',  1, NOW(), NOW()),
('zhangsan', '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS', 'zhangsan@example.com', 'USER',  1, NOW(), NOW()),
('lisi',     '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS', 'lisi@example.com',     'USER',  1, NOW(), NOW()),
('wangwu',   '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS', 'wangwu@example.com',   'USER',  1, NOW(), NOW()),
('zhaoliu',  '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS', 'zhaoliu@example.com',  'USER',  1, NOW(), NOW()),
('qianqi',   '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS', 'qianqi@example.com',   'USER',  1, NOW(), NOW()),
('sunba',    '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS', 'sunba@example.com',    'USER',  1, NOW(), NOW()),
('zhoujiu',  '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS', 'zhoujiu@example.com',  'USER',  1, NOW(), NOW()),
('wushi',    '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS', 'wushi@example.com',   'USER',  1, NOW(), NOW()),
('zhenghao', '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS', 'zhenghao@example.com', 'USER',  0, NOW(), NOW()),
('fengjie',  '$2a$10$2ZLqg7/SHE5V1ifeGK6N7.eiCQnRcSMCgahiCDbXx.JWi0hU.QRIS', 'fengjie@example.com',  'USER',  1, NOW(), NOW());

-- 10 条分类
INSERT INTO category (name, description, create_time) VALUES
('编程', '编程开发相关的Prompt', NOW()),
('学习', '学习提效相关的Prompt', NOW()),
('写作', '文章写作相关的Prompt', NOW()),
('办公', '日常办公相关的Prompt', NOW()),
('翻译', '语言翻译相关的Prompt', NOW()),
('营销', '市场营销相关的Prompt', NOW()),
('数据分析', '数据处理与可视化相关的Prompt', NOW()),
('设计', 'UI/UX设计相关的Prompt', NOW()),
('面试', '求职面试相关的Prompt', NOW()),
('生活', '日常生活辅助Prompt', NOW());

-- 12 条Prompt（涵盖不同分类和作者）
INSERT INTO prompt (title, content, description, category_id, user_id, view_count, favorite_count, status, create_time, update_time) VALUES
('Java代码优化助手', '你是一名Java专家，请优化以下代码，指出性能问题和改进建议：', '用于Java代码优化', 1, 1, 156, 12, 1, NOW(), NOW()),
('英语学习计划制定', '你是一名英语老师，请根据我的水平制定一份30天英语学习计划，我的水平是：', '制定个性化英语学习计划', 2, 1, 89, 8, 1, NOW(), NOW()),
('公众号文章润色', '你是一名资深编辑，请帮我润色以下公众号文章，使其更有吸引力：', '公众号文章润色使用', 3, 1, 203, 25, 1, NOW(), NOW()),
('Python数据分析脚本', '你是一名数据分析师，请帮我编写Python脚本完成以下数据分析任务：', '生成数据分析Python代码', 7, 3, 67, 5, 1, NOW(), NOW()),
('周报自动生成', '请根据我本周的工作内容，帮我生成一份结构清晰的周报：', '快速生成工作周报', 4, 2, 312, 45, 1, NOW(), NOW()),
('中英互译助手', '请将以下内容翻译成英文，保持专业术语准确：', '高质量中英互译', 5, 4, 178, 19, 1, NOW(), NOW()),
('小红书种草文案', '你是一名小红书运营达人，请帮我写一篇种草笔记，产品是：', '生成小红书风格文案', 6, 2, 421, 56, 1, NOW(), NOW()),
('SQL查询优化建议', '你是一名DBA，请分析以下SQL语句并给出优化建议：', '数据库SQL优化辅助', 1, 5, 94, 7, 1, NOW(), NOW()),
('UI设计需求文档', '请帮我撰写一份UI设计需求文档，应用场景是：', '生成UI设计需求文档', 8, 6, 56, 4, 1, NOW(), NOW()),
('面试模拟问答', '你是一名面试官，请针对Java后端开发岗位提问并评价我的回答：', '模拟技术面试', 9, 3, 267, 31, 1, NOW(), NOW()),
('旅行攻略生成', '请帮我制定一份3天的旅行攻略，目的地是：', '自动生成旅行攻略', 10, 7, 134, 15, 1, NOW(), NOW()),
('论文摘要润色', '你是一名学术论文审稿人，请帮我润色以下论文摘要：', '学术论文摘要优化', 3, 8, 78, 9, 1, NOW(), NOW());

-- 12 条收藏记录
INSERT INTO favorite (user_id, prompt_id, create_time) VALUES
(2, 1,  NOW()),
(2, 3,  NOW()),
(2, 5,  NOW()),
(3, 5,  NOW()),
(3, 6,  NOW()),
(4, 7,  NOW()),
(4, 3,  NOW()),
(5, 10, NOW()),
(5, 2,  NOW()),
(6, 6,  NOW()),
(7, 5,  NOW()),
(8, 10, NOW());

-- 12 条使用记录
INSERT INTO prompt_history (user_id, prompt_id, use_time) VALUES
(2, 1,  NOW()),
(2, 5,  NOW()),
(3, 5,  NOW()),
(3, 10, NOW()),
(4, 7,  NOW()),
(4, 6,  NOW()),
(5, 10, NOW()),
(5, 3,  NOW()),
(6, 6,  NOW()),
(7, 5,  NOW()),
(8, 10, NOW()),
(2, 3,  NOW());

-- 11 条状态变更审计日志（模拟历史启停记录；此时触发器尚未创建，为直接插入）
-- 与用户现状一致：zhenghao(id=11) 最终为禁用，fengjie(id=12)、lisi(id=4) 恢复为正常
INSERT INTO user_status_log (user_id, username, old_status, new_status, change_time) VALUES
(3,  'zhangsan', 1, 0, NOW() - INTERVAL 9 DAY),
(3,  'zhangsan', 0, 1, NOW() - INTERVAL 8 DAY),
(11, 'zhenghao', 1, 0, NOW() - INTERVAL 7 DAY),
(11, 'zhenghao', 0, 1, NOW() - INTERVAL 6 DAY),
(11, 'zhenghao', 1, 0, NOW() - INTERVAL 5 DAY),
(11, 'zhenghao', 0, 1, NOW() - INTERVAL 4 DAY),
(11, 'zhenghao', 1, 0, NOW() - INTERVAL 3 DAY),
(12, 'fengjie',  1, 0, NOW() - INTERVAL 2 DAY),
(12, 'fengjie',  0, 1, NOW() - INTERVAL 1 DAY),
(4,  'lisi',     1, 0, NOW() - INTERVAL 12 HOUR),
(4,  'lisi',     0, 1, NOW() - INTERVAL 6 HOUR);

-- =============================================
-- 权限管理子系统初始化数据：角色 / 权限点 / 绑定关系
-- =============================================

-- 3 个角色：USER（普通用户）/ ADMIN（管理员）/ SUPER_ADMIN（超级管理员）
INSERT INTO role (code, name, description, create_time) VALUES
('USER',         '普通用户',    '可发布/编辑/删除自己的提示词，浏览与收藏社区内容',                  NOW()),
('ADMIN',        '管理员',      '在普通用户权限之上，可管理用户、内容与分类，导出数据',              NOW()),
('SUPER_ADMIN',  '超级管理员',  '拥有系统全部权限，包括权限点的分配与维护（用户分组的最终治理者）',  NOW());

-- 16 个权限点（按模块分组）
INSERT INTO permission (code, name, module, description) VALUES
('prompt:view',          '查看提示词',     '提示词', '浏览列表、查看详情'),
('prompt:create',        '发布提示词',     '提示词', '新增自己的提示词'),
('prompt:edit:own',      '编辑自己提示词', '提示词', '编辑本人创建的提示词'),
('prompt:edit:any',      '编辑任意提示词', '提示词', '管理员编辑任意用户的提示词'),
('prompt:delete:own',    '删除自己提示词', '提示词', '删除本人创建的提示词'),
('prompt:delete:any',    '删除任意提示词', '提示词', '管理员删除任意用户的提示词'),
('favorite:toggle',      '收藏/取消收藏',  '提示词', '收藏或取消收藏任意提示词'),
('category:list',        '查看分类',       '分类',   '浏览分类列表'),
('category:create',      '新增分类',       '分类',   '管理员新增分类'),
('category:delete',      '删除分类',       '分类',   '管理员删除分类'),
('user:list',            '查看用户',       '用户',   '管理员查看用户列表'),
('user:manage',          '管理用户',       '用户',   '管理员启用/禁用用户'),
('data:view-stat',       '查看统计',       '数据',   '管理员查看系统统计'),
('data:export',          '导出数据',       '数据',   '管理员导出查询结果'),
('role:list',            '查看角色',       '权限',   '查看角色与权限绑定'),
('role:assign',          '分配权限',       '权限',   '为角色绑定或解绑权限点');

-- 角色-权限绑定（RBAC 核心数据）
-- USER：6 个基本权限
INSERT INTO role_permission (role_code, permission_code) VALUES
('USER', 'prompt:view'),
('USER', 'prompt:create'),
('USER', 'prompt:edit:own'),
('USER', 'prompt:delete:own'),
('USER', 'favorite:toggle'),
('USER', 'category:list');

-- ADMIN：USER 全部 + 管理类 10 个权限（合计 16 个，但 user:list 已包含 USER 没有，仅做加项）
INSERT INTO role_permission (role_code, permission_code) VALUES
('ADMIN', 'prompt:view'),
('ADMIN', 'prompt:create'),
('ADMIN', 'prompt:edit:own'),
('ADMIN', 'prompt:edit:any'),
('ADMIN', 'prompt:delete:own'),
('ADMIN', 'prompt:delete:any'),
('ADMIN', 'favorite:toggle'),
('ADMIN', 'category:list'),
('ADMIN', 'category:create'),
('ADMIN', 'category:delete'),
('ADMIN', 'user:list'),
('ADMIN', 'user:manage'),
('ADMIN', 'data:view-stat'),
('ADMIN', 'data:export'),
('ADMIN', 'role:list'),
('ADMIN', 'role:assign');

-- SUPER_ADMIN：拥有全部 16 个权限点
INSERT INTO role_permission (role_code, permission_code)
SELECT 'SUPER_ADMIN', code FROM permission;

-- =============================================
-- 数据库对象：视图 / 触发器 / 存储过程（数据库课设配套）
-- =============================================

-- 视图1：Prompt 全信息视图（关联分类名与作者名，管理后台内容管理、
-- 导出、存储过程查询均基于该视图，避免业务侧重复写三表连接）
DROP VIEW IF EXISTS v_prompt_full;
CREATE VIEW v_prompt_full AS
SELECT p.id, p.title, p.content, p.description,
       p.category_id, c.name AS category_name,
       p.user_id,     u.username AS author_name,
       p.view_count, p.favorite_count, p.status,
       p.create_time, p.update_time
FROM prompt p
LEFT JOIN category c ON p.category_id = c.id
LEFT JOIN sys_user u ON p.user_id = u.id;

-- 视图2：分类统计视图（每个分类的 Prompt 数、总浏览量、总收藏数，
-- 供管理后台数据统计使用）
DROP VIEW IF EXISTS v_category_stat;
CREATE VIEW v_category_stat AS
SELECT c.id AS category_id, c.name AS category_name,
       COUNT(p.id) AS prompt_count,
       IFNULL(SUM(p.view_count), 0)     AS total_view_count,
       IFNULL(SUM(p.favorite_count), 0) AS total_favorite_count
FROM category c
LEFT JOIN prompt p ON p.category_id = c.id
GROUP BY c.id, c.name;

-- 触发器：用户状态变更审计
-- 管理员在后台启用/禁用用户（UPDATE sys_user.status）时，
-- 自动把变更记录写入 user_status_log 审计日志表
DROP TRIGGER IF EXISTS trg_user_status_change;
DELIMITER $$
CREATE TRIGGER trg_user_status_change
AFTER UPDATE ON sys_user
FOR EACH ROW
BEGIN
    IF NEW.status <> OLD.status THEN
        INSERT INTO user_status_log (user_id, username, old_status, new_status, change_time)
        VALUES (NEW.id, NEW.username, OLD.status, NEW.status, NOW());
    END IF;
END$$
DELIMITER ;

-- 存储过程：按关键词与分类搜索 Prompt
-- 参数均可传 NULL 表示不过滤；关键词对标题与描述做模糊匹配
DROP PROCEDURE IF EXISTS sp_search_prompt;
DELIMITER $$
CREATE PROCEDURE sp_search_prompt(IN p_keyword VARCHAR(100), IN p_category_id BIGINT)
BEGIN
    SELECT v.id, v.title, v.description,
           v.category_name, v.author_name,
           v.view_count, v.favorite_count, v.create_time
    FROM v_prompt_full v
    WHERE (p_keyword IS NULL OR p_keyword = ''
           OR v.title LIKE CONCAT('%', p_keyword, '%')
           OR v.description LIKE CONCAT('%', p_keyword, '%'))
      AND (p_category_id IS NULL OR v.category_id = p_category_id)
    ORDER BY v.create_time DESC;
END$$
DELIMITER ;
