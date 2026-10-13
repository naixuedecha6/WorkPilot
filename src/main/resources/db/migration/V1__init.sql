-- WorkPilot 数据库初始结构
-- 第一版:用户、会话、消息、文档、文档分块、故障、Agent 运行、工具执行、审批、待办

-- ============== 用户 ==============
CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(32)  NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============== 会话 ==============
CREATE TABLE conversations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(255) NOT NULL DEFAULT '新会话',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_conv_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_conv_user_updated ON conversations(user_id, updated_at DESC);

-- ============== 消息 ==============
CREATE TABLE messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(16)  NOT NULL,        -- USER / ASSISTANT / SYSTEM / TOOL
    content         TEXT         NOT NULL,
    metadata        TEXT,                          -- JSON
    run_id          BIGINT,                        -- 若由 Agent 任务产生,关联 agent_runs
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);
CREATE INDEX idx_msg_conv ON messages(conversation_id, created_at);

-- ============== 文档 ==============
CREATE TABLE documents (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id        BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'UPLOADED',  -- UPLOADED/PROCESSING/INDEXED/FAILED
    error_message   TEXT,
    chunk_count     INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_doc_owner ON documents(owner_id, created_at DESC);

-- ============== 文档分块(向量库抽象后,这里只保留元数据 + 文本) ==============
CREATE TABLE document_chunks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id     BIGINT       NOT NULL,
    content         TEXT         NOT NULL,
    chunk_index     INT          NOT NULL,
    metadata        TEXT,                          -- JSON: page/chapter/source
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chunk_doc FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);
CREATE INDEX idx_chunk_doc ON document_chunks(document_id, chunk_index);

-- ============== 故障记录 ==============
CREATE TABLE incidents (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    severity        VARCHAR(16)  NOT NULL,         -- LOW/MEDIUM/HIGH/CRITICAL
    status          VARCHAR(32)  NOT NULL,         -- OPEN/IN_PROGRESS/RESOLVED/CLOSED
    category        VARCHAR(64)  NOT NULL,
    owner_id        BIGINT       NOT NULL,         -- 可见范围:仅本人
    occurred_at     TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inc_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_inc_owner_time ON incidents(owner_id, occurred_at DESC);
CREATE INDEX idx_inc_severity ON incidents(severity);
CREATE INDEX idx_inc_status ON incidents(status);

-- ============== Agent 运行 ==============
CREATE TABLE agent_runs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT,
    user_id         BIGINT       NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'CREATED',  -- CREATED/PLANNING/RUNNING/WAITING_APPROVAL/COMPLETED/FAILED/REJECTED/CANCELLED
    input           TEXT         NOT NULL,                    -- 原始任务请求
    plan            TEXT,                                    -- JSON
    context         TEXT,                                    -- JSON,控制敏感信息
    output          TEXT,                                    -- 最终结果
    error_message   TEXT,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_run_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_run_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE SET NULL
);
CREATE INDEX idx_run_user ON agent_runs(user_id, created_at DESC);
CREATE INDEX idx_run_status ON agent_runs(status);

-- ============== 工具执行 ==============
CREATE TABLE tool_executions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id          BIGINT       NOT NULL,
    step_id         VARCHAR(64)  NOT NULL,
    tool_name       VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,        -- PENDING/RUNNING/SUCCESS/FAILED/SKIPPED
    arguments       TEXT,                        -- JSON
    result          TEXT,                        -- JSON / summary
    error_code      VARCHAR(64),
    duration_ms     BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_te_run FOREIGN KEY (run_id) REFERENCES agent_runs(id) ON DELETE CASCADE
);
CREATE INDEX idx_te_run ON tool_executions(run_id, created_at);

-- ============== 审批 ==============
CREATE TABLE approvals (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id          BIGINT       NOT NULL,
    tool_execution_id BIGINT     NOT NULL,
    tool_name       VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',  -- PENDING/APPROVED/REJECTED
    payload         TEXT         NOT NULL,                    -- JSON:待执行参数
    reviewer_id     BIGINT,
    reviewed_at     TIMESTAMP,
    comment         TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appr_run FOREIGN KEY (run_id) REFERENCES agent_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_appr_te FOREIGN KEY (tool_execution_id) REFERENCES tool_executions(id) ON DELETE CASCADE
);
CREATE INDEX idx_appr_status ON approvals(status);
CREATE UNIQUE INDEX uq_appr_pending_per_step ON approvals(run_id, tool_execution_id, status);

-- ============== 待办 ==============
CREATE TABLE tasks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    creator_id      BIGINT       NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    priority        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',   -- LOW/MEDIUM/HIGH/URGENT
    status          VARCHAR(16)  NOT NULL DEFAULT 'OPEN',     -- OPEN/IN_PROGRESS/DONE/CANCELLED
    due_at          TIMESTAMP,
    source_run_id   BIGINT,
    idempotency_key VARCHAR(128),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_run FOREIGN KEY (source_run_id) REFERENCES agent_runs(id) ON DELETE SET NULL
);
CREATE UNIQUE INDEX uq_task_idem ON tasks(creator_id, idempotency_key);
CREATE INDEX idx_task_creator ON tasks(creator_id, status);

-- ============== 种子数据:测试用户 ==============
-- 密码 admin123 的 BCrypt 哈希(开发期固定,生产应改)
INSERT INTO users (username, password_hash, role) VALUES
    ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN'),
    ('demo',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'USER');

-- ============== 种子数据:故障示例 ==============
INSERT INTO incidents (title, description, severity, status, category, owner_id, occurred_at) VALUES
    ('订单服务接口超时', '调用 /api/orders 时 30% 请求超过 3s 返回,数据库连接池耗尽', 'HIGH', 'OPEN', 'PERFORMANCE', 2, CURRENT_TIMESTAMP - 2 DAY),
    ('登录页 500 错误', '前端登录页 POST /api/login 持续返回 500,日志显示 NullPointerException', 'CRITICAL', 'IN_PROGRESS', 'BUG', 2, CURRENT_TIMESTAMP - 1 DAY),
    ('报表导出乱码', '导出 Excel 时中文列名显示为问号,缺少 UTF-8 BOM', 'LOW', 'RESOLVED', 'BUG', 2, CURRENT_TIMESTAMP - 5 DAY),
    ('缓存命中率下降', 'Redis 命中率从 95% 跌到 60%,怀疑大量 key 集中过期', 'MEDIUM', 'OPEN', 'PERFORMANCE', 2, CURRENT_TIMESTAMP - 3 DAY),
    ('第三方支付回调失败', '微信支付回调签名校验失败,密钥可能已轮换', 'CRITICAL', 'OPEN', 'INTEGRATION', 2, CURRENT_TIMESTAMP - 6 HOUR),
    ('数据库慢查询', '用户列表查询 P95 达到 1.2s,未命中索引', 'MEDIUM', 'OPEN', 'PERFORMANCE', 2, CURRENT_TIMESTAMP - 1 DAY),
    ('消息队列堆积', 'Kafka consumer lag 持续增长,处理速度低于生产速度', 'HIGH', 'IN_PROGRESS', 'INFRA', 2, CURRENT_TIMESTAMP - 12 HOUR),
    ('前端白屏', '部分用户访问首页白屏,可能与新版本兼容性有关', 'HIGH', 'OPEN', 'BUG', 2, CURRENT_TIMESTAMP - 4 HOUR);
