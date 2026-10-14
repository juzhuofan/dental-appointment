# Spring AI 与 DeepSeek 模块

本模块在现有 Java 17、Spring Boot 3.5.16 工程中使用 Spring AI 1.0.9，默认模型为 `deepseek-flash`。小程序提供独立的“AI 助手”导航页，支持多轮聊天、诊所/医生咨询、真实号源查询及初步导诊。完整业务、接口和数据说明见 [AI-CHAT.md](AI-CHAT.md)。

## 目录与职责

```text
backend/src/main/java/com/dental/ai/
├─ config/       AiProperties、独立 ChatClient、诊所时区 Clock
├─ controller/   R 包装的配置、预约助手和通用文本接口
├─ service/
│  ├─ AiService.java                 供其他业务注入的统一接口
│  ├─ impl/SpringAiService.java       模型调用、结构化解析、并发与异常处理
│  ├─ PromptTemplateService.java      模板加载和变量渲染
│  ├─ AiRateLimitService.java         Redis 3.2 兼容的接口限流
│  ├─ AiConversationService.java      私有会话、消息、幂等与逻辑删除
│  ├─ AiChatAnswerService.java        真实资料和受限导诊的回答组装
│  └─ AppointmentGuideService.java    自然语言预约的业务示例
├─ dto/          外部输入、模型解析结果
├─ vo/           对外响应
├─ entity/       会话与消息，逻辑删除和时间字段
└─ mapper/       MyBatis-Plus 会话和消息 Mapper
backend/src/main/resources/prompts/ai/
├─ appointment-intent/  预约条件解析模板
│  ├─ system.st
│  └─ user.st
├─ clinic-chat/         多轮聊天计划模板
│  ├─ system.st
│  └─ user.st
└─ clinic-copy/         诊所文案整理示例模板
   ├─ system.st
   └─ user.st
miniapp/src/pages/ai/index.vue                    独立聊天页面
miniapp/src/components/AiChatMessage.vue           消息与医生号源卡片
miniapp/src/utils/ai-chat.js                       账号隔离、幂等发送与分页协调
```

会话和用户可见消息保存在 `ai_conversation`、`ai_message`，由 Flyway V5 建表。模型原始查询计划不持久化。两个表均含 `deleted`、`created_at`、`updated_at`；会话有版本号保护并发写入。诊所资料查询通过原业务 service 完成，不跨模块直接访问其他模块 Mapper。

## 本机配置

在 `backend/src/main/resources/application.yml` 选择 `dev` 或 `prod`；开关位于对应 Profile 的 `app.ai`，真实 API Key 写在 Git 忽略的 `application-private-dev.yml` 或 `application-private-prod.yml` 的同名节点：

```yaml
app:
  ai:
    enabled: true
    api-key: '填写本机 DeepSeek API Key'
```

修改对应私有 YAML 中已有的 `app.ai` 节点，不要重复建立 `app`。本机可以继续直接填写私有 YAML，公开版本库使用占位符；私有文件不提交 Git。密钥只由后端向 `https://api.deepseek.com` 发送，不返回小程序，不在业务日志中输出。

默认连接超时 3 秒、读取超时 25 秒、最大同时调用 3 次、每个登录账号每 60 秒最多 10 次接口请求。不自动重试付费调用。小程序 AI 请求超时为 35 秒，其他接口仍为 15 秒。关闭 AI 时基础服务可启动，模型客户端不创建；智能接口返回 `AI_DISABLED`，普通预约继续可用。

重启后端后重新加载微信开发者工具的小程序工程。生产构建目录为 `miniapp/dist/build/mp-weixin`；若使用 `npm run dev:mp-weixin`，开发目录为 `miniapp/dist/dev/mp-weixin`，两个目录不要混用。

## 其他模块如何调用

### 1. 新增模板

在 `resources/prompts/ai/<templateId>/` 新增 `system.st` 和 `user.st`。名称只允许小写字母、数字和连字符，例如 `notice-summary`。模板由服务器管理，客户端不能上传或指定本地文件路径。

`system.st` 示例：

```text
你是诊所公告编辑助手。请用一句话概括公告。
只使用原文信息，不添加时间、价格或诊疗承诺。
```

`user.st` 示例：

```text
公告原文：
{content}
```

模板变量使用 `{变量名}`。模板文件以 UTF-8 编码。需要传入 JSON 资料时，先由业务层序列化后作为变量传入；JSON 输出结构由统一服务的转换器格式说明添加。

### 2. 注入统一接口

```java
private final AiService aiService;

public NoticeSummaryService(AiService aiService) {
    this.aiService = aiService;
}

public String summarize(String content) {
    return aiService.generate("notice-summary", Map.of("content", content));
}
```

业务需要结构化字段时定义自己的 DTO，再调用：

```java
public record SummaryDTO(String summary, List<String> keywords) { }

SummaryDTO result = aiService.generateStructured(
        "notice-summary", Map.of("content", content), SummaryDTO.class);
```

统一服务使用 Spring AI `ChatClient`、`PromptTemplate` 和 `BeanOutputConverter` 的 JSON Schema 格式说明。结果通过 Jackson 解析，解析失败返回统一业务异常。调用方还应验证返回字段是否属于自己的合法业务范围；预约助手已检查目录 ID、日期和时间段。

新增业务 HTTP 接口时，继续以 `R<DTO>` 接收输入、`R<VO>` 返回结果，并在模型调用前执行认证和 `AiRateLimitService.check(CurrentUser.require().id())`。网络调用期间不要开启数据库写事务，也不要把模型输出直接作为 SQL、命令或预约写入操作。

## HTTP 接口

| 方法与地址 | 权限 | 用途 |
| --- | --- | --- |
| `GET /api/v1/ai/config` | 公开 | 返回 `enabled` 和 `maxMessageLength=300` |
| `POST /api/v1/ai/guide` | 已登录 | 自然语言预约推荐 |
| `POST /api/v1/ai/generate` | 管理员 | 按服务器模板生成文本 |

预约请求：

```json
{
  "data": {
    "message": "这周末下午想洗牙",
    "departmentId": null,
    "doctorId": null
  }
}
```

响应 `R.data` 包含：

| 字段 | 含义 |
| --- | --- |
| `message` | 后端生成的处理提示 |
| `needsClarification` | 是否需要补充预约目的和条件 |
| `purpose` | 简短就诊目的 |
| `dateFrom`、`dateTo` | 实际查询日期，未指定时默认今天起 14 天 |
| `timeOfDay` | `ANY`、`MORNING`、`AFTERNOON`、`EVENING` |
| `weekdays` | 1 至 7，对应周一至周日；空数组表示不限 |
| `candidates` | 最多 5 位医生，每位最多 3 个可预约时段 |
| `moreAvailable` | 是否有更多时段可到医生详情查看 |

候选医生包含 `doctorId`、`doctorName`、`title`、`departmentId`、`departmentName`、`specialty` 和 `schedules`。排班沿用现有 `ScheduleVO`，时间和剩余号源全部从数据库查询。模型不会返回排班 ID。

页面选中医生或科室时，请求传入对应 ID，后端与模型条件取交集，不改变页面选择。上午指排班开始时间 12 点前，下午为 12 点至 18 点前，晚上为 18 点后。仅允许未来 90 天内且跨度不超过 31 天的日期查询。未匹配、约满、停诊或超出可预约截止时间的号源不推荐，不自动放宽条件。

通用文本请求示例：

```json
{
  "data": {
    "templateId": "clinic-copy",
    "variables": { "text": "诊所周六上午开诊，预约患者请提前十分钟到院。" }
  }
}
```

响应为 `R<AiGenerateVO>`，包含 `templateId` 和 `content`。普通患者无权限调用通用文本接口。

## 小程序交互

底部导航为“首页、AI 助手、预约、我的”。聊天入口独立于首页和医生页，未登录沿用现有微信登录流程，并正确返回 AI 导航页。每条最多 300 字，页面说明聊天会按账号保存，并提醒不填写姓名、电话等身份信息。

聊天回答可带真实医生与号源卡片。点击“选择”时再查一次最新排班余量，然后进入现有确认预约页选择就诊人并提交。最终预约仍由原事务、幂等键和行锁保证。聊天支持新对话、历史、分页和逻辑删除；重试保持发送标识和原文，切换账号清空展示并丢弃旧账号的异步响应。原 `POST /api/v1/ai/guide` 保留供已有业务调用方复用。

## 失败处理

| 错误码 | HTTP | 处理方式 |
| --- | --- | --- |
| `AI_DISABLED` | 503 | 当前未开放，手动预约 |
| `AI_BUSY` / `AI_RATE_LIMITED` | 429 | 稍后重试 |
| `AI_UNAVAILABLE` | 503 | 上游或限流存储暂不可用，手动预约 |
| `AI_INVALID_RESPONSE` | 502 | 输出无法解析或 ID 不合法，重新描述 |
| `INVALID_ARGUMENT` | 400 | 请求、模板参数或日期范围不合法 |

业务日志仅记录上游异常类型，不记录 API Key、请求头、需求全文或模型失败原文。本模块支持诊所咨询、预约筛选与初步导诊，诊断和治疗需由医生检查确定。

## 验证方法

```powershell
$env:JAVA_HOME = 'C:\jdk\jdk17'
# 在 backend 目录：常规测试、打包，不调用付费模型
& 'D:\itApps\maven\apache-maven-3.10.0\bin\mvn.cmd' `
  -s 'D:\itApps\maven\apache-maven-3.10.0\conf\settings.xml' verify
# 显式执行虚构资料的真实模型聊天验证，会使用本机 API Key
& 'D:\itApps\maven\apache-maven-3.10.0\bin\mvn.cmd' `
  -s 'D:\itApps\maven\apache-maven-3.10.0\conf\settings.xml' `
  '-Dtest=AiChatDeepSeekLiveTest' '-Dai.live=true' test
```

项目根目录运行 `npm run test:ai` 测试聊天协调逻辑；在 `miniapp` 下运行 `npm run build:mp-weixin` 构建微信小程序。聊天数据库测试通过 `AiConversationDatabaseTest` 显式启用，使用独立数据库；详细命令见 [AI-CHAT.md](AI-CHAT.md)。

官方参考：[Spring AI DeepSeek 1.0 文档](https://docs.spring.io/spring-ai/reference/1.0/api/chat/deepseek-chat.html)、[DeepSeek API 文档](https://api-docs.deepseek.com/)。

## 2026-10-09 初次模型接入验证记录

- 后端全量 `verify` 成功：150 项常规测试通过，3 项真实接入测试默认跳过。
- 单独启用的真实 DeepSeek 解析测试通过：正确解析“这周末下午想洗牙”的日期、时段和科室。
- 真实应用上下文、JWT 认证、MySQL 公开目录、Redis 限流及预约助手接口联调通过；返回号源与数据库逐项核对。
- 小程序组件与既有微信登录逻辑共 24 项测试通过。
- 微信小程序生产构建和 H5 生产构建均成功。生产压缩依赖已固定为 `terser 5.51.2`。
- 真机页面显示和实际微信登录后操作需要在微信开发者工具重新编译后扫码验证。
