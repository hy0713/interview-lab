# Interview Lab · AI 模拟面试系统

面向大学生求职练习的前后端分离项目。Java 17 + Spring Boot 单体后端，通过 Spring AI DeepSeek Starter 与 ChatClient 调用 DeepSeek；Vue 提供从创建面试、多轮问答到报告复盘的流程。当前已实现 P0 核心体验与 P1 产品功能，没有引入 Spring Cloud Alibaba 或微服务。

## 已实现

- **账号与权限**：候选人、面试官双角色；BCrypt、JWT、Redis 登出撤销；候选人只能访问自己的面试。
- **题库与出题**：分页搜索、难度筛选、管理题目；按岗位描述调用 DeepSeek 生成题目；创建时保存独立快照。
- **上下文追问**：分析已提交的回答和前序对话，针对错误、遗漏或含糊内容追问。每道主问题最多一次、每场最多 0～3 次；达到 85 分且没有明确错误时停止追问，将拓展点放入复盘建议。追问继承题型并紧跟对应主问题。
- **练习与模拟**：练习模式支持暂停、恢复；模拟模式禁止暂停。分析期间和题目之间不计时，点击继续才启动下一题。支持超时、刷新恢复、浏览器草稿、提前交卷和取消面试。
- **按题型评分**：技术题、行为题、场景题采用不同评分维度。AI 提供维度分及理由，后端计算加权分；引用必须逐字出现在本轮回答中。
- **报告复盘**：等待全部评分成功后生成优势、短板和学习计划；按主问题与追问展示对话、原文证据、遗漏、错误、改进建议和参考要点。答题期间隐藏分数与参考答案。
- **失败可见**：评分或报告调用失败保留答案和任务状态，页面显示原因并提供重试，不生成替代分数。
- **运行状态**：面试官概览显示真实 MySQL、Redis、RabbitMQ 连通性；可手动请求 DeepSeek 检查。
- **部署**：Docker Compose、Nginx 代理、Flyway 增量数据库迁移、Swagger UI。

前端每 2.5 秒同步面试详情。当前使用文本问答，未实现语音视频面试、流式输出或简历 RAG。

## P1：从面试到持续复练

- 面试官登录后进入独立工作台，筛选候选人、指派面试、查看评分进度，填写人工评语与独立人工评分。
- 题库增加岗位分类、知识点标签、审核发布、驳回、收藏及知识点组卷；新建或编辑的题目审核通过后才能公开和抽题。
- 候选人可从历史低分回答创建同题练习，在独立报告中并排比较前后回答，并查看最近 30 场成绩趋势、题型和知识点表现。
- 报告列表与报告详情使用独立页面，保留任务进度与失败重试。题型表现图与成长趋势使用 Vue / SVG。

详见 [P1 功能与验收](docs/P1.md)。真实验收中，同题首答 10 分、复练后 93 分；人工分另存为 75 分，没有覆盖 AI 原分。此结果用于验证功能流程，不能作为评分准确率证明。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 语言 / 框架 | Java 17、Spring Boot 3.5.7 |
| AI | Spring AI 1.1.0、DeepSeek ChatModel、ChatClient、结构化实体转换 |
| 持久化 | MySQL 8、MyBatis-Plus 3.5.12、Flyway |
| 鉴权 | Spring Security、JJWT 0.12.6、BCrypt、Redis 撤销列表 |
| 异步与计时 | RabbitMQ、Redis Sorted Set、Spring Scheduling |
| 前端 | Vue 3、Vite 5、Element Plus、Pinia、Vue Router、Axios |
| 部署 / 验证 | Docker Compose、Nginx、JUnit 5、MockMvc、H2、Mockito |

## 快速启动：Docker

先启动 Docker Desktop，在项目根目录执行：

```powershell
# 仅首次创建；已有 .env 时直接编辑，避免覆盖现有 Key
Copy-Item .env.example .env
# 编辑根目录 .env，填写 DEEPSEEK_API_KEY
docker compose up -d --build
```

| 地址 | 用途 |
| --- | --- |
| http://localhost:3000 | 前端 |
| http://localhost:8080/swagger-ui.html | API 文档 |
| http://localhost:8080/api/health | 应用存活检查 |
| http://localhost:15673 | RabbitMQ 管理，默认 interview / interview_dev |

演示账号：candidate / Interview123!（候选人），admin / Interview123!（面试官）。仅在 `DEMO_DATA=true` 时按用户名幂等创建。默认面试官注册邀请码为 `interview-lab`，可通过 `INTERVIEWER_INVITE_CODE` 修改。

演示题库包含 33 道已发布题目，覆盖 Java 后端、前端开发、软件测试与通用岗位，并带知识点标签。修改 .env 中数据库或 RabbitMQ 密码不会修改已有数据卷中的账号，首次设置后保持一致。

没有 Key 也能浏览题库和进行题库面试；评分与报告会显示失败，配置 Key、重启后端后可以重试。Key 仅保存在服务端根目录 .env，该文件已忽略，不需要在聊天或前端填写。

```powershell
docker compose logs -f backend
docker compose down
```

`down` 保留数据卷；`down -v` 会清空本项目容器数据，仅在明确需要重置时使用。

如果 Maven 基础镜像下载失败，可以在本机先打包，再使用本地 JAR 构建镜像。本机已验证此路径：

```powershell
mvn -f ai-mock-interview/pom.xml package -DskipTests
docker compose -f compose.yml -f compose.jar.yml up -d --build
```

Windows 如提示 8080 无法绑定，在根目录 .env 设置 `BACKEND_PORT=9090` 后重新启动。前端仍为 http://localhost:3000，后端与 Swagger 地址相应改为 9090。启动脚本、Vite 代理和联调脚本均读取此配置；容器内部端口保持 8080。

## 本地开发

环境：JDK 17、Maven 3.8+、Node.js 20.19+。先启动中间件：

```powershell
docker compose up -d mysql redis rabbitmq
```

将 JDK 17 和 Maven 加入 PATH，根目录 .env 填好后执行：

```powershell
# 读取 .env、打包并运行；已打包时省略 -Build
.\scripts\run-backend.ps1 -Build
```

另开终端：

```powershell
cd ai-mock-interview-frontend
npm ci
npm run dev
```

前端默认 3000，Vite 将 `/api` 代理到后端 8080。宿主机中间件端口为 MySQL 3307、Redis 6380、RabbitMQ 5673。

自建中间件时可设置 `MYSQL_URL`、`MYSQL_USER`、`MYSQL_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USER`、`RABBITMQ_PASSWORD`。默认数据库为 `ai_interview_v2`，MySQL 连接强制会话时区 +08:00；启动脚本和容器采用 Asia/Shanghai，期限保存至毫秒。

DeepSeek 配置：

- `DEEPSEEK_API_KEY`：有效 Key，根目录 .env。
- `DEEPSEEK_BASE_URL`：默认 `https://api.deepseek.com`，不要拼接 `/chat/completions`。
- `DEEPSEEK_MODEL`：默认 `deepseek-flash`，按账号可用模型调整。
- Spring AI ChatClient 负责请求与结构化实体转换。当前模型默认思考模式可能消耗输出预算；`DeepSeekRequestCustomizer` 通过 Spring RestClient 扩展点为指定 DeepSeek 请求添加 `thinking: {type: disabled}`。Spring AI 1.1 原生 options 没有此字段，后续升级支持后可移除此适配。

## 流程与评分规则

```text
CREATED → IN_PROGRESS ⇄ PAUSED（仅练习）
                 │
                 ├─ 答题 → ANALYZING → READY → 点击继续 → 下一题或追问
                 ├─ 超时 → 当前题 0 分 → READY（不会自动启动后续题）
                 ├─ 全部完成 / 提前交卷 → EVALUATING → COMPLETED
                 └─ 取消 → CANCELLED

答案与 SCORE 任务落库 → 提交事务后发 RabbitMQ → Spring AI 分析
所有单题评分成功 → REPORT 任务 → Spring AI 总结 → 完整报告
```

| 题型 | 评分维度与权重 |
| --- | --- |
| TECHNICAL 技术题 | 专业正确性 60%、推理与实践 25%、表达结构 15% |
| BEHAVIORAL 行为题 | 情境与行动细节 40%、个人贡献与结果 35%、反思与迁移 25% |
| SCENARIO 场景题 | 问题分析 40%、方案与执行 35%、取舍与风险 25% |

单题分数由后端按维度权重计算并四舍五入；先对每组主问题和追问取平均，再对所有主问题组平均并四舍五入，避免追问较多的主题占据额外权重。超时、提前交卷未答题计 0 分。原文引用匹配只能验证证据来自回答，不能保证 AI 判断一定正确。

- **并发与幂等**：提交、超时、暂停、取消及 AI 结果保存锁定同一面试行；父问题唯一约束防止重复追问。取消或提前交卷后不再追加追问。
- **计时**：MySQL 保存期限，提交时检查；Redis Sorted Set 提供到期索引。每 5 秒扫描到期项、每分钟补查数据库；页面按服务端时间校正。暂停保存剩余毫秒并清除期限，恢复时重新建立期限。
- **异步任务**：PENDING / RUNNING / SUCCEEDED / FAILED 落库，条件更新认领；每 10 秒补发待处理任务。运行超过 10 分钟标记失败，允许手动重试。
- **事务边界**：外部 AI 调用不占用数据库长事务；快照读取与结果写入分别使用短事务。
- **失败与报告**：评分失败暂停推进；报告等待全部单题评分成功。取消保留已有数据，取消时正在执行的外部请求可能仍产生费用。

这是单实例求职项目，未实现多实例任务租约或外部 API 恰好执行一次。

## 目录

```text
train/
├── compose.yml / .env.example
├── scripts/                   # 本地启动、真实联调
├── docs/                      # 验证记录与截图
├── ai-mock-interview/
│   ├── pom.xml / Dockerfile
│   └── src/
│       ├── main/java/com/example/interview/
│       │   ├── ai/            # 结构化问答、证据与题型评分
│       │   ├── config/        # 框架与 DeepSeek 请求适配
│       │   ├── domain/        # 用户、题库、面试、对话、任务
│       │   ├── service/       # 面试流程、暂停、计时、异步任务
│       │   ├── security/      # JWT、权限与撤销
│       │   └── web/           # REST API
│       ├── main/resources/db/migration/
│       └── test/
└── ai-mock-interview-frontend/
    └── src/                   # Vue 页面、状态与接口
```

旧源码备份位于 `.local/legacy-20261019/`，P0 前源码备份位于 `.local/p0-before/`，均不参与编译或镜像。新库 `ai_interview_v2` 使用 `mi_*` 表，未覆盖旧项目数据库；V2/V3 增量迁移补充多轮字段与期限精度。

## 验证

```powershell
mvn -f ai-mock-interview/pom.xml test
cd ai-mock-interview-frontend
npm run build
cd ..
docker compose config --quiet
```

真实环境已启动且演示账号可用时，可运行以下脚本。它会创建带 P0 标记的题库与面试样本，并实际调用 DeepSeek，产生 API 费用：

```powershell
.\scripts\p0-live-check.ps1
```

38 项自动化测试覆盖流程、追问预算、停止条件、暂停恢复、并发提交、超时、权限、失败重试、证据及真实 Spring AI HTTP 协议。另有真实中间件和 DeepSeek 联调，结果与边界见 [验证记录](docs/VERIFICATION.md)，接口与页面步骤见 [验收指南](ai-mock-interview/API_TEST_GUIDE.md)。本项目不要求生产级压测。

## 官方参考

- [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/1.1/api/chatclient.html)
- [Spring AI DeepSeek](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html)
- [DeepSeek Chat API](https://api-docs.deepseek.com/api/create-chat-completion)
- [MyBatis-Plus](https://baomidou.com/en/getting-started/)
