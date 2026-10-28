-- kb-platform 业务库（MySQL 8.0）
-- docker 首次启动会自动执行本文件；改了脚本要先 docker compose down -v 清卷
SET NAMES utf8mb4;

-- 建库 + 切库。容器方式下 MYSQL_DATABASE 已经建过库了，这两句是幂等的；
-- 手工在本机 MySQL 上执行时，这两句是必须的（否则报 No database selected）。
CREATE DATABASE IF NOT EXISTS kb_platform
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE kb_platform;

CREATE TABLE IF NOT EXISTS kb_user
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 密文',
    nickname    VARCHAR(64)           DEFAULT NULL COMMENT '昵称',
    role        VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

CREATE TABLE IF NOT EXISTS kb_knowledge_base
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(128) NOT NULL COMMENT '知识库名称',
    description   VARCHAR(512)          DEFAULT NULL,
    owner_id      BIGINT       NOT NULL COMMENT '归属用户',
    scope         VARCHAR(16)  NOT NULL DEFAULT 'PRIVATE' COMMENT 'PRIVATE/TEAM',
    doc_count     INT          NOT NULL DEFAULT 0 COMMENT '文档数',
    chunk_size    INT          NOT NULL DEFAULT 800,
    chunk_overlap INT          NOT NULL DEFAULT 150,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_owner (owner_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='知识库';

CREATE TABLE IF NOT EXISTS kb_document
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    kb_id         BIGINT       NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_type     VARCHAR(32)           DEFAULT NULL,
    file_size     BIGINT       NOT NULL DEFAULT 0,
    file_path     VARCHAR(512) NOT NULL COMMENT '本地存储路径',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSING/SUCCESS/FAILED',
    chunk_count   INT          NOT NULL DEFAULT 0,
    error_msg     VARCHAR(1024)         DEFAULT NULL,
    parse_cost_ms BIGINT       NOT NULL DEFAULT 0,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kb_status (kb_id, status, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='文档';

CREATE TABLE IF NOT EXISTS kb_conversation
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    kb_id             BIGINT      NOT NULL,
    user_id           BIGINT      NOT NULL,
    title             VARCHAR(128)         DEFAULT NULL,
    message_count     INT         NOT NULL DEFAULT 0,
    last_message_time DATETIME             DEFAULT NULL,
    deleted           TINYINT     NOT NULL DEFAULT 0,
    create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user (user_id, last_message_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='会话';

CREATE TABLE IF NOT EXISTS kb_message
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id   BIGINT      NOT NULL,
    msg_role          VARCHAR(16) NOT NULL COMMENT 'USER/ASSISTANT',
    content           TEXT        NOT NULL,
    ref_chunks        TEXT                 DEFAULT NULL COMMENT '引用的切片，JSON',
    prompt_tokens     INT         NOT NULL DEFAULT 0,
    completion_tokens INT         NOT NULL DEFAULT 0,
    cost_ms           BIGINT      NOT NULL DEFAULT 0,
    create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conv (conversation_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='消息';

-- Spring AI 对话记忆的持久化表：Redis 是热缓存，这张表是冷备。
-- payload 是 Kryo 序列化后的字节流（List<StoredTurn>），恢复时反序列化回 Message 列表。
CREATE TABLE IF NOT EXISTS ai_chat_memory
(
    conversation_id VARCHAR(128) NOT NULL COMMENT '会话 ID（ChatMemory 的 memoryId）',
    payload         LONGBLOB     NOT NULL COMMENT 'Kryo 序列化的对话记忆',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Spring AI 对话记忆';
