# WorkPilot — 基于 Spring AI 的企业知识与业务任务 Agent 平台

> Java 21 · Spring Boot 3.4 · Spring AI 1.0 · DeepSeek / OpenAI 兼容协议
>
> 单体优先、显式状态机、人工审批、写操作幂等、引用溯源

## 这是什么

WorkPilot 是一个**面向简历的工程级 Java Agent 平台**。它不仅能"聊天",
更关键的是把模型能力接入了一个**有状态、可审计、可审批**的后端工作流:

- 用户给一个模糊需求(比如"分析最近的故障,生成报告后建一个跟进待办")
- 后端让 LLM 把它拆成结构化计划
- 按计划逐步调工具(`query_incidents` / `summarize_incidents` / `generate_report` / `create_task`)
- 涉及写操作的工具,**必须人工审批**才能继续
- 全程的步骤、参数、结果、错误都持久化到数据库,可逐条回放

## 技术栈

| 层次 | 技术 |
|---|---|
| 后端 | Java 21 + Spring Boot 3.4 + Spring AI 1.0 |
| LLM | DeepSeek-Chat(OpenAI 兼容协议) |
| 持久化 | H2(开发) / PostgreSQL(生产) + JPA + Flyway |
| 向量库 | SimpleVectorStore(开发,文件持久化) / pgvector(生产) |
| 文档解析 | spring-ai-pdf / spring-ai-markdown / spring-ai-tika |
| 前端 | 单页 Thymeleaf + 原生 JS(无构建工具) |
| 测试 | JUnit 5 + Mockito |

## 运行

### 1. 准备 JDK 21

```powershell
$env:JAVA_HOME = "D:\coding\jdk21\jdk-21.0.12"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

### 2. 配置 API Key

`src/main/resources/application.yml` 已经写了默认 Key(开发期可用)。
生产请改用环境变量:

```bash
export OPENAI_API_KEY=sk-xxxxxxxx
export OPENAI_API_BASE=https://api.deepseek.com
```

### 3. 启动

```powershell
cd d:\project\java-agent
mvn -DskipTests package
java -jar target\workpilot-0.1.0.jar
```

启动成功后访问 `http://localhost:8080`。首次访问会显示登录页:

| 账号 | 密码 | 角色 |
|---|---|---|
| demo | admin123 | USER |
| admin | admin123 | ADMIN |

也支持点击"注册"创建新账号。

H2 控制台:`http://localhost:8080/h2-console`,JDBC URL = `jdbc:h2:file:./data/workpilot`。

## 演示场景

### 场景 1:RAG 问答

1. 切到「**知识库**」,上传一份 PDF / Markdown / TXT(比如你的项目文档)
2. 等待状态变 `INDEXED`(后台异步处理,通常几秒)
3. 切到「**聊天**」问:`我的项目文档里关于性能优化的部分说了什么?`

### 场景 2:多步骤 Agent + 报告生成

聊天输入:

```
分析最近的故障,生成排查报告
```

系统会自动:
1. 调 `summarize_incidents` 统计严重程度和分类
2. 调 `query_incidents` 拉详细记录
3. 调 `generate_report` 基于证据生成结构化报告(区分"事实"与"建议")
4. 完成后到「**报告**」Tab 查看报告全文和证据链;或「**Agent**」Tab 点"查看轨迹"回放

### 场景 3:写操作审批

聊天输入:

```
查询故障后创建一个高优先级跟进待办
```

执行到 `create_task` 步骤时,系统**暂停**等审批:
- 左侧菜单「**审批**」点"批准" → Agent 继续 → 待办建好
- 点"拒绝" → 整个 Run 标记 `REJECTED`,不执行写操作

### 场景 4:故障管理

切到「**故障**」Tab:
- 顶部统计卡片:总数 / OPEN / IN_PROGRESS / RESOLVED / CLOSED
- 筛选条:严重度 / 状态 / 类别
- 左侧表,右侧详情。详情卡片底部有"→ IN_PROGRESS / RESOLVED / CLOSED"快捷状态流转
- "+ 新建故障" 弹窗:填表即可入库

### 场景 5:用户隔离

1. 右上角"退出"
2. 在登录页用「注册」建一个全新账号(比如 `test1`)
3. `test1` 看到的故障 / 报告 / 待办 / Agent 运行 / 知识库检索 都为空(因为数据属于原用户)
4. 访问别人的 run id 也会被 403 拒绝

### 场景 6:RAG 引用展示

1. 「**知识库**」上传一份文档(Markdown / PDF / TXT 都可以)
2. 「**知识库**」Tab 上半部分"试试搜索"输入关键词,直接看命中片段(包含来源文件名 + 第几段)
3. 切到「**聊天**」输入:`查知识库里关于 X 的内容,分析最近故障并生成报告`
4. AI 回复下方会自动显示 **📚 参考了知识库 N 条片段** 的卡片,每条标出:
   - 来源文档名 + 第几段
   - 相关度(如有)
   - 原文片段
5. 「**报告**」Tab 选一份报告,报告下方同样会显示引用的知识库片段
6. 「**Agent**」Tab 展开任意运行轨迹,会同时显示工具调用步骤 + 知识库引用

### 场景 3:审批流(写操作必须批准)

聊天输入:

```
分析最近的故障,生成报告后创建一个跟进待办,标题是"处理高频性能问题",优先级 HIGH
```

你会看到:
1. 状态变成 `WAITING_APPROVAL`
2. 切到「**审批**」,看到待执行操作:工具 `create_task`、参数、用途
3. 点"批准并继续",后端从原步骤恢复,创建待办
4. 切到「**待办**」可看到新建的跟进项,带 `sourceRunId` 和 `idempotencyKey`

### 场景 4:幂等保护

重复点"批准并继续",或者让 Agent 反复请求相同的 `create_task`:
- 同一 `run + title` 只创建一条待办
- 第二次返回 `idempotent: true`,不会重复写入

## 架构与状态机

```
用户消息
   ↓
ChatController(启发式分流:任务型 vs 普通问答)
   ↓
ChatService → AgentOrchestrator.start()
   ↓
AgentPlanner  ← ChatClient
   │           (生成结构化 JSON 计划)
   ↓
AgentPolicy 校验(白名单/最大步数/参数长度/审批覆盖)
   ↓
AgentOrchestrator.runLoop()
   ↓
   ├─ 只读工具 → AgentExecutor.execute() → ToolExecution(SUCCESS)
   └─ 写工具   → 创建 Approval + 暂停运行
                  ↓  批准
                  runLoop 继续(从原步骤恢复,不重做)
```

对应文档 §7.2 的状态机:

```
CREATED → PLANNING → RUNNING ─→ COMPLETED
                       │   ─→ FAILED
                       │   ─→ WAITING_APPROVAL → RUNNING(恢复) / REJECTED
```

## 目录结构

```
src/main/java/com/workpilot/
├── WorkPilotApplication.java
├── config/         # AI、Web、CORS 配置
├── controller/     # 4 个 REST 入口
├── service/        # 业务服务层
├── agent/          # 状态机、计划器、策略、执行器、编排器
├── tools/          # 5 个业务工具
├── entity/         # 10 张表的 JPA 实体 + 9 个枚举
├── repository/     # 10 个 JPA Repository
├── dto/            # AgentPlan/AgentStep/ToolResult 等
├── exception/      # 业务异常 + 全局处理器
└── common/
```

## 关键设计决策

| 决策 | 理由 |
|---|---|
| **Plan-and-Execute 而不是 ReAct** | 先生成完整计划,经服务端校验,再逐步执行;审批可以在任意步骤插入 |
| **工具白名单 + 审批覆盖** | 模型只能从 `ToolRegistry` 里选工具;`requiresApproval` 始终以服务端 Tool 声明为准,模型不能绕过 |
| **ToolExecution 持久化** | 每次工具调用都入库,带 stepId/参数/结果/耗时,故障可定位 |
| **写操作幂等** | `create_task` 用 `(runId, title)` 哈希作为幂等键,数据库唯一约束兜底 |
| **长事务避免** | 模型调用与事务解耦,审批持久化与恢复执行分离,事务内不等待外部 API |
| **工具可测试** | 所有工具实现 `Tool` 接口,只依赖 `ToolContext` 与 `MapLike` 参数,易 mock |

## 评测与验证

### 单元测试

```powershell
mvn test
```

覆盖:
- `WorkPilotApplicationTests` — 上下文加载
- `AgentPolicyTest` — 计划校验、工具白名单、审批覆盖、最大步数
- `IncidentToolsTest` — 工具参数解析、limit 钳位

### 手动验证(对应文档 §14.4 安全测试用例)

- [x] 同一会话、同一 run,重复批准不会重复创建待办
- [x] 工具名称不在白名单时,后端拒绝执行(由 `AgentPolicy` 强制)
- [x] 日志中不出现 API Key(`application.yml` 用占位符,真实 Key 只在环境变量)
- [x] 用户只能看自己的 `incidents`(`IncidentRepository.search` 强制 `userId` 过滤)

## 切换到生产数据库(PostgreSQL + pgvector)

1. 改 `application.yml` 的 `spring.datasource.url` 为 PostgreSQL
2. 注释掉 `AiConfig` 里的 `SimpleVectorStore` Bean,启用 `spring-ai-starter-vector-store-pgvector`
3. 修改 `V1__init.sql` 适配 PostgreSQL(`BIGSERIAL`、`JSONB`、`pgvector` 类型)
4. Flyway 迁移会自动执行

## Docker Compose(后续)

```yaml
version: '3.8'
services:
  app:
    build: .
    ports: ["8080:8080"]
    environment:
      OPENAI_API_KEY: ${OPENAI_API_KEY}
    depends_on: [db, vector]
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: workpilot
      POSTGRES_PASSWORD: workpilot
    ports: ["5432:5432"]
    volumes: ["./data/postgres:/var/lib/postgresql/data"]
  vector:
    image: pgvector/pgvector:pg16
    # ...同 db,但启用 vector 扩展
```

## 已实现 vs 待扩展

**第一版已实现**(对应文档 §16 功能验收):
- [x] 在线模型 API 对话(DeepSeek)
- [x] SSE 流式输出
- [x] PDF / Markdown / TXT 文档处理
- [x] 向量检索 + 引用溯源
- [x] 5 个工具(知识检索/故障查询/故障统计/报告生成/创建待办)
- [x] 多步骤 Agent 任务
- [x] 显式状态机(8 种状态,严格迁移)
- [x] 写操作审批 + 幂等
- [x] Agent 运行状态查询 + 执行轨迹
- [x] 用户注册 / 登录(JWT, BCrypt 密码)
- [x] 用户数据隔离(用户只能访问自己的数据)
- [x] Spring Security RBAC(USER / ADMIN 角色)
- [x] 故障管理页面(筛选 / CRUD / 状态流转 / 统计)
- [x] 报告中心页面(从 agent_runs 提取 generate_report 工具的产物)
- [x] 待办手动创建 / 状态流转 / 删除
- [x] Agent 轨迹详情(内嵌展开,含 plan 解析、参数、证据链、引用)
- [x] RAG 引用卡片(聊天/报告/Agent 轨迹三处都展示来源片段)
- [x] 知识库直接检索(知识库 Tab 的"试试搜索"功能)
- [x] 知识库 owner_id 隔离(SimpleVectorStore 应用层过滤)
- [x] 三模式主题(跟随系统 / 浅色 / 深色,localStorage 持久化,系统主题实时响应)
- [x] CSS 变量主题化(全站 30+ 颜色 token,所有组件跟随主题切换)
- [x] 流畅过渡(主题切换 / 按钮按下 / 卡片 hover,0.15-0.25s 动画)
- [x] 现代化设计语言(渐变主色 / 微妙阴影 / 圆角 / 玻璃拟态)

**生产化待扩展**(对应文档 §2.3 / §19):
- [ ] 完整 RBAC 权限(ADMIN 专属 API)
- [ ] PostgreSQL + pgvector
- [ ] Docker Compose / K8s
- [ ] 评测集(30-50 条 RAG、20-30 条 Agent 任务)
- [ ] 演示视频与 README 截图
