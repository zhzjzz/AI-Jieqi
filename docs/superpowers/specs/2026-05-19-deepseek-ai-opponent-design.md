# DeepSeek API AI 对弈功能 Spec

## 1. 背景

当前项目已经具备揭棋联机骨架：

- `AI-Jieqi-master/src/main/java/org/example/common/GameBoard.java` 负责棋盘初始化、坐标转换、原始位置类型判断。
- `AI-Jieqi-master/src/main/java/org/example/common/Piece.java` 和 `Move.java` 提供基础棋子与走法协议。
- `AI-Jieqi-master/src/main/java/org/example/common/RuleEngine.java` 已支持明子、暗子按原始位置移动、翻子、将军、困毙等基础规则校验。
- `AI-Jieqi-master/src/main/java/org/example/net/GameServer.java` 已通过 Socket 为红黑双方分配同一随机种子并转发 `Move`。
- `AI-Jieqi-master/src/main/java/org/example/ui/GameApp.java` 已提供真人点击对弈界面。

PRD 的核心目标是交付一个“可对弈、可解释、可测试、可复现”的 AI agent。下一阶段不重写联机和 GUI，而是在现有规则骨架上新增 DeepSeek API 驱动的 AI 对弈能力。

## 2. 目标

本 spec 定义一个 DeepSeek API AI 对弈层，满足：

- AI 可作为一个客户端接入现有 Socket 服务器，与真人或另一个客户端对弈。
- AI 每回合在本地生成合法候选走法，再调用 DeepSeek API 从候选中选择一步。
- AI 输出仍使用现有 `Move` 协议，兼容服务端和 GUI。
- DeepSeek 返回必须被本地解析、校验和兜底，不能让模型直接绕过规则引擎。
- API key、模型、超时、候选数量、提示词参数可配置。
- 每步记录候选、模型选择、解析结果、合法性校验、耗时和兜底原因，便于实验报告复盘。

## 3. 非目标

本阶段不做：

- 重新实现完整揭棋搜索引擎、Alpha-Beta 或 Expectiminimax。
- 复杂 GUI 改造。
- 数据库、账号、排行榜或公网匹配。
- 将 DeepSeek API key 写入源码或提交到仓库。
- 让 DeepSeek 直接维护唯一棋局状态。权威状态仍由本地 `GameBoard`、`RuleEngine` 和服务器广播的 `Move` 决定。

## 4. 可选方案

### 方案 A：DeepSeek 直接输出 `Move`

把棋盘状态发给 DeepSeek，让模型直接返回 `source`、`destination`、`type`。

优点是实现少，提示词简单。缺点是模型容易返回非法走法、坐标错误或与本地状态不一致，且难以证明 AI 每步合法。

结论：不采用。

### 方案 B：本地生成合法候选，DeepSeek 只选择候选 ID

本地用 `RuleEngine` 枚举所有合法走法，给每个候选分配稳定 ID，DeepSeek 只能返回候选 ID 和简短理由。

优点是合法性边界清晰，解析简单，测试容易。缺点是 AI 水平依赖提示词和候选摘要，模型看不到完整搜索树。

结论：作为最小可交付方案采用。

### 方案 C：候选约束 + 本地启发式兜底

在方案 B 基础上，为候选增加本地启发式评分；API 超时、返回非法 JSON、候选 ID 不存在、模型输出无法通过二次校验时，选择本地最高分候选。

优点是稳定性最好，能满足对弈和验收要求；API 不可用时仍可继续运行。缺点是需要多写一个轻量启发式评分器。

结论：推荐采用，并作为本 spec 的设计方案。

## 5. DeepSeek API 约束

截至 2026-05-19，DeepSeek 官方文档显示：

- OpenAI 兼容 base URL 为 `https://api.deepseek.com`。
- Chat Completions 路径为 `/chat/completions`。
- 当前推荐模型包括 `deepseek-v4-flash` 和 `deepseek-v4-pro`。
- 旧模型名 `deepseek-chat`、`deepseek-reasoner` 将在 2026-07-24 停用；本项目默认不使用旧模型名。
- API 使用 Bearer token 认证。
- 对局决策请求应使用非流式响应，降低客户端复杂度。
- 请求应启用 JSON 输出或等价的严格解析约束，提示模型只返回指定 JSON 对象。

默认配置：

```properties
deepseek.api.baseUrl=https://api.deepseek.com
deepseek.api.model=deepseek-v4-flash
deepseek.api.thinking=disabled
deepseek.api.timeoutMillis=8000
deepseek.ai.maxCandidates=40
deepseek.ai.fallback=heuristic
```

`DEEPSEEK_API_KEY` 从环境变量读取。若未设置，AI 客户端进入本地启发式模式，并在日志中记录 `fallbackReason=missing_api_key`。

## 6. 架构

新增包建议放在 `org.example.ai`：

- `AiClientMain`：AI 客户端启动入口，参数与现有 `ClientMain` 对齐，例如 `host port`。
- `AiGameClient`：连接 Socket 服务器，接收颜色、种子、轮次消息和对手走法，轮到自己时调用 `AiAgent`。
- `AiAgent`：AI 决策门面，输入本地棋局和阵营，输出 `AiDecision`。
- `LegalMoveGenerator`：基于现有 `RuleEngine` 枚举所有合法 `Move`，包括原地翻暗子。
- `BoardSnapshotFormatter`：把当前棋局、己方阵营、候选走法、历史摘要格式化为 DeepSeek prompt。
- `DeepSeekClient`：封装 HTTP 请求、超时、响应解析和错误分类。
- `DeepSeekMoveSelector`：调用 DeepSeek，让模型在候选 ID 中选择。
- `HeuristicMoveSelector`：本地兜底选择器，按吃将、吃子价值、翻子、机动性等轻量规则排序。
- `AiDecision`：记录最终走法、候选 ID、来源、模型名、耗时、评分、理由、兜底原因。
- `AiDecisionLogger`：将每回合决策写入 JSON Lines 或控制台。

现有 `GameBoard` 目前没有深拷贝和完整局面序列化。AI 第一版可以只在当前棋盘上枚举候选，不做深层模拟；若后续接 Alpha-Beta，再补 `GameState`、`BoardCloner` 和不可变快照。

## 7. 对弈数据流

1. 启动 `GameServer`。
2. 启动真人客户端或另一个 AI 客户端。
3. 启动 `AiClientMain host port`。
4. `AiGameClient` 接收 `COLOR:*`、`SEED:*`、`YOUR_TURN:*`，用种子初始化本地 `GameBoard`。
5. 收到服务器广播的 `Move` 时，本地调用与 GUI 一致的应用逻辑更新棋盘。
6. 当 `YOUR_TURN:true` 时：
   - `LegalMoveGenerator` 枚举候选。
   - `HeuristicMoveSelector` 为候选给出兜底排序。
   - `DeepSeekMoveSelector` 在配置允许且 API key 存在时请求 DeepSeek。
   - 返回候选 ID 后，二次调用 `RuleEngine.isLegalMove` 校验。
   - 校验通过则发送该 `Move`；否则记录原因并发送启发式候选。
7. 服务端仍作为权威裁判。若服务端返回 `ERROR:*`，AI 记录错误并等待下一次轮次消息，不盲目重发。

## 8. 候选走法格式

候选 ID 必须在单次请求内稳定，建议格式：

```text
C001
C002
C003
```

发送给模型的候选摘要：

```json
{
  "id": "C017",
  "source": "b3",
  "destination": "b4",
  "kind": "MOVE",
  "piece": "RED_HIDDEN",
  "target": "EMPTY",
  "heuristicScore": 12
}
```

`kind` 取值：

- `MOVE`：移动到空位。
- `CAPTURE`：吃子。
- `REVEAL`：原地翻开己方暗子。
- `CAPTURE_GENERAL`：吃将/帅，最高优先级。

第一版只把本地已知信息发给 DeepSeek。因为当前服务器会把翻开后的 `type` 广播给双方，项目第一版不做信息不完全视角隔离；如果课程要求隐藏被吃暗子类型，再把该限制写入后续 spec。

## 9. DeepSeek 请求与响应

系统提示词约束：

```text
你是揭棋 AI 的候选走法选择器。你不能发明走法，只能从候选列表中选择一个 id。
返回必须是 JSON 对象，不要输出 Markdown，不要输出额外解释。
```

用户消息包含：

- 当前己方阵营。
- 棋盘 10x9 简表。
- 最近若干步历史。
- 当前是否被将军。
- 候选列表，最多 `deepseek.ai.maxCandidates` 个。
- 选择原则：优先胜利、避免立即被吃将、优先高价值吃子、其次改善机动性，无法判断时选择最高启发式评分。

期望响应：

```json
{
  "candidateId": "C017",
  "confidence": 0.72,
  "reason": "吃掉对方高价值明子，同时不会暴露将帅"
}
```

解析规则：

- 只接受 JSON object。
- `candidateId` 必须存在于本轮候选表。
- `confidence` 若不存在则记为 `null`，不影响对弈。
- `reason` 最多保留 200 个字符，避免日志过大。
- 响应解析失败、候选不存在、二次校验失败、HTTP 非 2xx、超时、API key 缺失时，统一走本地兜底。

## 10. 本地启发式兜底

`HeuristicMoveSelector` 不追求强棋力，只保证稳定、合法、可解释。

建议评分：

- 吃对方将/帅：`+100000`。
- 吃明子：按类型价值加分，车 900、炮 450、马 400、象 220、士 200、兵 100、将 100000。
- 吃暗子：按对方剩余未揭示棋子池的平均价值加分；第一版若没有 `HiddenPiecePool`，用固定平均值 330。
- 原地翻己方暗子：`+40`，若当前无安全吃子则提高到 `+80`。
- 普通移动：`+10`。
- 走法后若本地规则校验不通过：直接排除。
- 候选排序相同则按候选 ID 稳定排序，保证复现。

后续若实现 PRD 中的 `HiddenPiecePool` 和 `Evaluator`，兜底评分可以平滑替换为正式评估函数。

## 11. 配置与安全

配置来源优先级：

1. 命令行参数。
2. 环境变量。
3. `src/main/resources/ai.properties` 默认值。

敏感信息：

- `DEEPSEEK_API_KEY` 只能从环境变量读取。
- 日志不得输出完整 API key。
- HTTP 错误日志最多记录状态码、错误类别和响应前 500 个字符。

建议环境变量：

```text
DEEPSEEK_API_KEY=...
DEEPSEEK_MODEL=deepseek-v4-flash
DEEPSEEK_TIMEOUT_MILLIS=8000
AI_MAX_CANDIDATES=40
```

## 12. 测试策略

新增测试应优先覆盖可离线行为，不依赖真实 DeepSeek API。

单元测试：

- `LegalMoveGeneratorTest`：初始局面能生成合法候选；每个候选都通过 `RuleEngine.isLegalMove`。
- `HeuristicMoveSelectorTest`：存在吃将候选时优先吃将；无 API 时返回合法候选。
- `DeepSeekResponseParserTest`：合法 JSON、非法 JSON、未知候选 ID、缺字段均能正确处理。
- `AiAgentFallbackTest`：API key 缺失、HTTP 超时、返回非法候选时进入兜底。

集成测试：

- 使用 fake `DeepSeekClient` 返回指定候选 ID，验证 `AiAgent` 输出对应 `Move`。
- 使用 fake `DeepSeekClient` 返回非法候选，验证输出启发式最高分候选。
- 在短超时配置下跑一次 AI 决策，确认不会阻塞超过配置上限。

真实 API smoke test：

- 默认不纳入 `mvn test`。
- 仅当设置 `DEEPSEEK_API_KEY` 且显式启用 `-Ddeepseek.smoke=true` 时运行。
- 只验证 API 可调用、JSON 可解析、候选 ID 合法，不依赖模型选择固定结果。

## 13. 验收标准

功能验收：

- 可以启动 `GameServer`、真人 `ClientMain` 和 `AiClientMain` 完成至少一局人机对弈流程。
- AI 每次发送的都是本地 `RuleEngine` 校验通过的 `Move`。
- DeepSeek API 不可用时，AI 仍能用启发式兜底继续下棋。
- 每步产生可复盘日志，包含候选数、最终来源 `DEEPSEEK` 或 `HEURISTIC_FALLBACK`、耗时、候选 ID、走法和原因。
- 不需要 GUI 的 AI 客户端可运行。

质量验收：

- 不把 API key 写入源码、日志或测试 fixture。
- DeepSeek 请求有超时控制。
- DeepSeek 响应有严格解析和二次合法性校验。
- 核心决策逻辑可通过 fake client 离线测试。
- spec 与 PRD 保持一致：本地规则权威、AI 输出 `Move`、日志可解释、测试可复现。

## 14. 风险与决策

- **模型返回不稳定**：通过候选 ID 约束、JSON 解析和本地兜底控制风险。
- **DeepSeek API 费用和网络延迟**：默认模型用 `deepseek-v4-flash`；候选数限制为 40；真实 API 测试默认关闭。
- **当前棋盘模型缺少完整 GameState**：第一版只做单步候选选择；搜索和正式评估放到后续实现。
- **信息不完全规则尚未细化**：第一版沿用当前服务端广播 `type` 的全信息状态；若老师要求隐藏信息，需要新增 `PlayerView` 和双视角棋局状态。
- **现有 RuleEngine 与 PRD 存在差异**：当前规则引擎会禁止走后被将军和将帅照面，而 PRD 提到“不考虑不应将这种错误”。本阶段不修改规则语义，只在 spec 中记录该差异；若后续课程确认，需要单独调整规则引擎。

## 15. 交付物

- DeepSeek AI 对弈 spec：本文档。
- 后续 implementation plan：拆分为候选生成、DeepSeek 客户端、AI agent、Socket AI 客户端、日志、测试六部分。
- 后续代码交付时应补充 `README.md` 人机对弈启动命令和环境变量说明。

## 16. 参考资料

- 项目 PRD：`2026-05-18-jiqi-ai-agent-prd.md`
- DeepSeek 官方 API 文档：`https://api-docs.deepseek.com/`
- DeepSeek Chat Completions 文档：`https://api-docs.deepseek.com/api/create-chat-completion`
- DeepSeek 更新日志：`https://api-docs.deepseek.com/updates/`
