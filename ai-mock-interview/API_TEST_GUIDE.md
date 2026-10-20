# P0 验收指南

先按根目录 README 启动项目。前端 http://localhost:3000，Swagger http://localhost:8080/swagger-ui.html。候选人 candidate / Interview123!，面试官 admin / Interview123!。

后端宿主端口由根目录 .env 的 `BACKEND_PORT` 控制；本机为 9090，Swagger 为 http://localhost:9090/swagger-ui.html。联调脚本自动读取此端口，也可显式传入 `-BaseUrl`。

## 1. 对话与报告

1. 创建练习模式面试，题库来源、2 道主问题、每题 60 秒、追问上限 1。
2. 开始后提交一段不完整回答，进入“分析中”；此时没有下一题倒计时，分数和参考答案不可见。
3. 分析完成后点击继续，若存在追问应先出现对应追问。追问指向实际回答中的缺口，每道主问题最多一次；下一题开始前不计时。
4. 回答追问及其余主问题。已回答对话按主问题和追问顺序保留。
5. 最后一题评分完成后生成报告。检查三个维度的分数、理由及权重，证据原文应可在对应回答中逐字找到。
6. 查看覆盖点、遗漏、错误、改进建议、参考答案和整体学习计划。
7. 使用完整回答另做一场比较：追问不是每题必有；达到 85 分且没有明确错误时不追加追问。

模型输出会有波动，不能把单次演示分数视为准确率证明。技术、行为、场景题应分别展示各自评分维度。

## 2. 计时与暂停

1. 练习模式答题中写入草稿，刷新后仍为同一题，草稿恢复，期限不重置。
2. 点击暂停，刷新后保持暂停；继续后从剩余时间计时。
3. 模拟模式没有暂停按钮；直接调用 pause 应返回 409。
4. 创建 2 题面试，第一题不答，约到期后 5 秒内显示超时、当前题计 0 分。
5. 等待一段时间再回来，第二题仍未开始。点击继续后才建立第二题期限。
6. 分析期间也可暂停练习；AI 分析完成不会擅自启动下一题。

## 3. 结束、失败与权限

1. 提前交卷将未答题计 0 分，等待已提交回答完成评分后生成报告；不再插入追问。
2. 取消面试后保留已提交内容，不再继续作答或生成新追问。
3. AI 失败显示具体的安全提示，保留回答，可重试评分或报告。配置 Key 后重启服务才能加载新值。
4. 两个候选人不能读取或控制对方的面试，包括 next / pause / resume / cancel。
5. 重复提交、重复消息与重试不会重复插入追问。
6. 登出后旧 JWT 返回 401，候选人无法管理题库或访问系统检查。

## 4. 面试官

1. 概览显示 MySQL、Redis、RabbitMQ 真实连通性。
2. 点击 AI 检查会实际请求 DeepSeek，产生少量 API 费用；未点击时只显示配置与模型名称。
3. 管理题库、搜索筛选、按岗位描述 AI 出题。
4. 修改题库后，历史面试快照内容保持原样。

## API 速查

接口返回 `{code,message,data}`，失败同时设置 HTTP 状态。受保护接口使用 `Authorization: Bearer <token>`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | /api/auth/register、/api/auth/login、/api/auth/logout | 注册、登录、撤销 token |
| GET | /api/auth/me | 当前用户 |
| GET | /api/questions | 题库分页 |
| POST / PUT / DELETE | /api/questions、/api/questions/{id} | 面试官管理题库 |
| POST | /api/questions/generate | 面试官 AI 出题入库 |
| GET | /api/interviews/stats | 角色对应统计 |
| GET / POST | /api/interviews | 列表、创建 |
| GET | /api/interviews/{id} | 面试、对话、评分与报告 |
| POST | /api/interviews/{id}/start | 首次开始 |
| POST | /api/interviews/{id}/turns/{turnId}/answer | 提交当前回答 |
| POST | /api/interviews/{id}/next | 分析完成后开始下一题或追问 |
| POST | /api/interviews/{id}/pause | 暂停练习 |
| POST | /api/interviews/{id}/resume | 恢复练习 |
| POST | /api/interviews/{id}/finish | 提前交卷 |
| POST | /api/interviews/{id}/cancel | 取消面试 |
| POST | /api/interviews/{id}/retry | 重试失败任务 |
| GET | /api/system/status | 面试官依赖检查 |
| POST | /api/system/ai-check | 面试官实际 AI 检查 |

创建请求：

```json
{"position":"Java 后端","jobDescription":"应届生 Java 后端","difficulty":"MEDIUM","source":"BANK","count":2,"secondsPerQuestion":60,"mode":"PRACTICE","maxFollowUps":1}
```

`mode` 可选 PRACTICE / EXAM，省略为 PRACTICE；`maxFollowUps` 范围 0～3，省略为 3。只有题目 ACTIVE 时接收非空 `{"answer":"回答内容"}`。等待分析、未确认下一题或暂停时不能提交。

## 自动真实联调

```powershell
.\scripts\p0-live-check.ps1
```

使用真实 MySQL、Redis、RabbitMQ 和 DeepSeek，验证部分回答与完整回答的追问差异、证据原文、评分报告、AI 出题、暂停恢复、超时及 JWT 撤销。会产生少量 API 费用，样本与结果保存到被忽略的 `.local/p0-live-results.json`。已完成的不完整回答样本可用 `-ReusePartialId <id>` 复用，减少重复调用。

单人走通上述流程即可作为当前阶段验收，无需生产压测、混沌测试或微服务环境。

## P1 产品功能

新增面试官工作台、指派、题库审核收藏、知识点组卷、错题复练、同题对比和独立报告。接口、统计口径及验收步骤见 [P1 指南](../docs/P1.md)。新建或修改题库题目后需审核发布才能组卷；候选人通过报告详情的同题重答入口创建新的练习。
