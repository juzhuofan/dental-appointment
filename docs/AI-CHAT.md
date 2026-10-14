# 小程序 AI 聊天助手

## 功能与界面

底部导航为“首页、AI 助手、预约、我的”。原首页和医生页的输入式 AI 组件已移除。独立聊天页支持新对话、历史查看、历史分页、逻辑删除、消息重试和账号切换清空展示。使用现有微信登录，登录后回到 AI 导航页。

每条消息最多 300 字，登录账号最多保留 100 个活动会话，每个会话最多 100 轮。输入和最近对话会发送给 DeepSeek，页面说明用途并提醒用户不要填写姓名、电话等身份信息。客户端不把健康聊天存入本地存储，历史从认证后的后端读取。

| 场景 | 功能 |
| --- | --- |
| 诊所咨询 | 地址、营业时间、电话、介绍、预约取消规则、当前已发布公告 |
| 医生咨询 | 真实医生姓名、科室、职称、擅长与简介，附医生详情卡片 |
| 号源咨询 | 结合历史医生、日期和时间偏好查询真实已发布号源 |
| 口腔问题 | 追问部位、持续时间、年龄等信息，提供初步就诊方向和有出处的风险提示 |

选中号源后重新调用原公开排班接口核实余量，再进入原预约确认页选择就诊人、提交预约。AI 不执行预约写入。

## 业务分层

```text
ai/controller/AiConversationController
  → ai/service/AiConversationService        账号隔离、幂等、历史、逻辑删除
    → ai/service/AiConversationLockService  Redis 会话锁
    → ai/service/AiChatAnswerService        查询/导诊计划与事实组装
      → AiService                          Spring AI ChatClient 多轮结构化调用
      → AiClinicKnowledgeService           最新诊所、公告、科室和医生公开资料
      → AiClinicalGuidanceService          固定追问、风险规则与公开出处
      → AppointmentGuideService.recommend  复用真实号源筛选，无第二次模型请求
    → ai/mapper/AiConversationMapper / AiMessageMapper
      → ai/entity/AiConversationEntity / AiMessageEntity
```

简单数据库操作由 service 使用 MyBatis-Plus 条件构造器完成。诊所和排班查询使用原模块服务，原模块已有的复杂查询仍由 Mapper/XML 执行。

`prompts/ai/clinic-chat/system.st` 和 `user.st` 定义聊天计划解析。Spring AI 请求带最近 12 条数据库消息，即最近 6 轮上下文；历史角色仅允许 USER 和 ASSISTANT，客户端不能提交系统提示、角色或其他账号历史。医生卡片的姓名和 ID 会补入上下文，支持“第一个医生”“那周末呢”等续问。聊天记忆来自应用保存的上下文，不训练或改变模型参数。

## 事实回答与初步导诊

每轮读取最新公开资料，模型输出受限制的 topic、资料字段、目录 ID、日期/时间、追问编码和导诊级别。模型不生成用于显示的诊所地址、营业时间、医生资料、诊断或处方。后端验证目录 ID、日期、枚举与追问编码后，从真实资料和固定模板组装回答。

首版口腔导诊依据 [NHS 牙痛页面](https://www.nhs.uk/symptoms/toothache/) 和 [NHS 口腔感染相关急诊提示](https://www.nhs.uk/conditions/dental-abscess/) 整理；资料核对日期为 2026-10-09。回答附可复制的出处链接。只提供中性的科室选择、追问和就诊提示，不给出疾病概率、确诊、处方或药物剂量。出现明确当前危险信号时，规则先返回急诊提示，跳过付费模型调用和普通号源推荐。否认和假设描述不能直接当成已发生症状。

首版没有经过口腔医生的临床验证，用于毕业设计演示；真实医疗应用需要由专业人士审核导诊规则及适用范围。危险信号规则覆盖有限，不声称能够排除紧急情况。

## 数据库设计（Flyway V5）

`ai_conversation`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT UNSIGNED | 自增主键 |
| user_id | BIGINT UNSIGNED | 从认证上下文取得的所属账号 |
| title | VARCHAR(80) | 第一条消息的简短标题，默认“新会话” |
| version | INT | 乐观版本号，默认 0 |
| deleted | TINYINT | 逻辑删除：0 活动、1 删除 |
| created_at | DATETIME(3) | UTC 新增时间 |
| updated_at | DATETIME(3) | UTC 修改时间 |

索引：`idx_ai_conversation_user(user_id, deleted, updated_at, id)`。

`ai_message`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT UNSIGNED | 自增主键，也作为历史分页游标 |
| conversation_id | BIGINT UNSIGNED | 所属会话 |
| request_id | VARCHAR(64) ASCII/ascii_bin | 区分大小写的发送重试标识 |
| role | VARCHAR(16) | USER 或 ASSISTANT |
| content | TEXT | 用户可见消息 |
| metadata_json | JSON，可空 | 回答 topic、导诊级别、医生号源卡片及出处快照 |
| deleted | TINYINT | 逻辑删除 |
| created_at | DATETIME(3) | UTC 新增时间 |
| updated_at | DATETIME(3) | UTC 修改时间 |

唯一索引：`uk_ai_message_request(conversation_id, request_id, role)`。历史索引：`idx_ai_message_conversation(conversation_id, deleted, id)`。

删除会话时，同一事务更新会话与消息的 `deleted`、`updated_at`，不执行物理 DELETE。医生卡片是回答时的快照，历史号源选择时必须重新查询。

## 接口契约

全部会话接口需要 Bearer 登录认证。所属 userId 从服务端认证上下文读取，不能由请求指定。其他账号的会话统一返回 `NOT_FOUND`，避免透露其存在情况。

| 方法与路径 | 输入 | 响应 R.data |
| --- | --- | --- |
| GET `/api/v1/ai/conversations` | page=1、size=20（最大50） | PageResult：records、total、page、size |
| POST `/api/v1/ai/conversations` | `{"data":{}}` | AiConversationVO：id、title、createdAt、updatedAt |
| GET `/api/v1/ai/conversations/{id}/messages` | 可选 beforeId | records（按 ID 升序）、hasMore、nextBeforeId |
| POST `/api/v1/ai/conversations/{id}/messages` | R 包装的 requestId、content | conversation、userMessage、assistantMessage |
| DELETE `/api/v1/ai/conversations/{id}` | 无 JSON 请求体 | null |

发送示例：

```json
{
  "data": {
    "requestId": "chat-20261009-abcdef123",
    "content": "那这周末下午有他的号源吗？"
  }
}
```

消息响应字段为 `id`、`requestId`、`role`、`content`、`answer`、`createdAt`。用户消息 answer=null；助手 answer 包含 `content`、`topic`、`guidance`、`candidates`、`references`。候选卡片沿用 `AppointmentGuideVO.Candidate` 和 `ScheduleVO`；出处为 title、url。

同一次重试必须保持 requestId 和内容一致。已保存的请求直接重放原消息对，不再次调用模型；同一标识改内容返回 `IDEMPOTENCY_CONFLICT`。历史每次读取最近 50 条，使用 nextBeforeId 请求更早消息。

## 并发与事务

- Redis 3.2 兼容的会话锁有效期 90 秒。同一会话正在回复时，其他发送或删除返回 `AI_CONVERSATION_BUSY`（409）。
- 模型调用在数据库写事务之外执行。生成答案后，短事务校验会话账号、活动状态和版本，再写入用户消息与助手消息；任一步失败整体回滚。
- 会话版本号防止锁过期或异常并发造成旧上下文覆盖新会话状态。
- 非紧急的模型调用继续使用账号每 60 秒 10 次、全局并发 3 次、连接 3 秒/读取 25 秒，不自动重试付费请求。
- 客户端未知发送结果保留 requestId 和原文，输入暂时锁定，用户可重试或开始新会话。账号切换会清空旧消息、输入和待重试状态，并丢弃旧异步响应。

## 本机使用

API Key 和 enabled 位于 `backend/src/main/resources/application-dev.yml`、`application-prod.yml` 的 `app.ai`，无需增加凭据。重新加载 Maven，重启后端时 Flyway 自动执行 V5 建表。微信开发者工具重新编译 `miniapp/dist/build/mp-weixin` 后，底部可见“AI 助手”。

建议人工真机验证：询问诊所地址和营业时间；询问某医生擅长方向后续问“那周末下午呢”；描述口腔问题并回答追问；从卡片进入预约确认；删除会话；退出登录并换账号，确认旧会话不可见。

## 测试与限制

常规后端 `mvn verify`、项目根目录 `npm run test:ai` 与微信/H5 构建不产生付费模型调用。

在 backend 目录，显式执行独立数据库验证：

```powershell
$env:JAVA_HOME = 'C:\jdk\jdk17'
& 'D:\itApps\maven\apache-maven-3.10.0\bin\mvn.cmd' `
  -s 'D:\itApps\maven\apache-maven-3.10.0\conf\settings.xml' `
  '-Dtest=AiConversationDatabaseTest' '-Dai.database=true' test
```

该测试使用独立 `dental_appointment_ai_test` 数据库和 Redis DB 1，按需创建并迁移，不写人工演示库或默认 Redis DB 0，不调用模型；测试记录结束后逻辑删除保留验证轨迹。数据库验证覆盖账号隔离、消息对原子保存、失败回滚、幂等重放、两表逻辑删除及历史分页。

真实模型测试使用虚构诊所资料和虚构症状，会产生少量 API 调用：将上面的测试参数改为 `'-Dtest=AiChatDeepSeekLiveTest' '-Dai.live=true'`。已验证诊所问题识别、医生选择与周末偏好跨轮继承，以及受限的症状追问。

浏览器验证使用独立 H5 开发预览（默认 6184 端口）与虚构接口夹具，运行 `npx playwright test tests/ui/ai-chat.spec.mjs`。浏览器测试内注入虚构登录状态，仅验证界面，不代替真实微信授权或生产认证。覆盖移动布局、聊天、历史删除、来源展示以及已约满号源处理。真机显示和实际微信登录仍须扫码验收。

## 2026-10-09 验证记录

| 验证 | 结果 |
| --- | --- |
| 后端全量 verify | 175 项常规测试通过，11 项需显式启用的测试默认跳过；打包成功 |
| 独立 MySQL + Redis DB 1 | 5 项通过，包括真实插入失败回滚、账号隔离、幂等和两表逻辑删除 |
| 真实 DeepSeek | 3 项通过：诊所事实字段、跨轮医生/周末条件、受限症状追问 |
| 客户端状态与登录回归 | 27 项通过，包括账号切换、重试原文、并发点击和临时重新验证登录状态 |
| 375×812 浏览器页面 | 3 项通过：聊天/历史删除/布局、出处展示、已约满号源保护；无页面脚本异常 |
| 微信小程序 + H5 | 最终生产构建成功 |

本轮真实模型使用虚构资料，数据库测试使用独立库并逻辑删除测试记录。没有手动执行业务库迁移，业务库在后端重启时自动应用 V5；没有提交或推送 Git。测试专用 6184 端口预览已停止。
