# 基于小程序的口腔诊所门诊预约系统
## 项目设计与实现基线（初版开发蓝图）

> 文档版本：1.1
> 编制日期：2026-10-05
> 本次修订：按项目要求固定 Java 17、MySQL 8.0.45、Redis 3.2.100、Node.js 24.18.0；补充阿里巴巴 Java 开发手册黄山版、全表逻辑删除及新增/修改时间字段、前后端 `R<T>` 请求/响应包装规范。
> 目标：以本文件作为代码初版的统一实现依据。未明确的细节按本文建议实现，并在代码、SQL 与接口文档中保持一致。

## 1. 项目定义

### 1.1 项目目标

实现一套面向单家口腔诊所的门诊预约系统。患者在微信小程序端浏览诊所、科室、医生和可预约排班，提交、查询或取消自己的预约；诊所管理员在 Vue Web 后台管理用户、科室、医生、排班、预约和公告；医生可查看个人日程并更新接诊状态。

项目采用前后端分离、模块化单体架构。第一版以“患者预约成功—诊所查看/处理—患者查询状态”的完整闭环为交付重点。系统不提供诊断建议、处方、支付、电子病历或真实医保服务。

### 1.2 范围边界

**第一版必须完成**

- 账号认证、角色授权、患者资料维护。
- 科室与医生资料查询和后台维护。
- 医生排班创建、发布、停诊和余量查询。
- 小程序端创建预约、查看本人预约、按规则取消预约。
- 后台筛选预约、确认预约、完成接诊或登记爽约。
- 公告展示、操作日志、统一错误响应和基础部署文档。

**明确不纳入第一版**

- 在线问诊、诊断、处方、支付、退款、电子病历、检查影像。
- 多诊所/多租户、复杂审批流、消息队列、微服务拆分。
- 真实微信订阅消息、短信验证码及微信支付。可以在完成核心流程后作为扩展。

### 1.3 成功验收口径

1. 管理员能创建医生排班，小程序能看到已发布且仍可预约的排班。
2. 患者能提交预约，并在小程序“我的预约”和后台列表看到一致记录。
3. 同一患者不能重复占用同一排班；并发情况下有效预约数不超过排班容量。
4. 合规取消只释放一次号源；已完成、已取消预约不能重复变更。
5. 患者不能读取或修改他人的预约；医生只能操作分配给自己的预约；管理员操作有权限校验。
6. 按本文步骤能够在本地启动前端、后端、MySQL 与 Redis。

## 2. 技术栈与工具版本

本项目优先采用成熟、相互兼容的稳定版本线，不为追逐最新大版本而增加升级成本。具体补丁版本应在创建工程时固定到构建文件和锁文件中；以下是建议的项目基线。

| 用途 | 技术 | 版本基线 | 说明 |
|---|---|---|---|
| 后端语言 | Java | 17 | 编译目标统一为 17；Spring Boot 3.5 支持 Java 17 |
| 后端框架 | Spring Boot | 3.5.16 | 单体 REST API；采用 Jakarta 命名空间 |
| Web | Spring MVC | 由 Boot BOM 管理 | 不单独覆盖 Spring Framework 版本 |
| ORM | MyBatis Spring Boot Starter | 3.0.x | 与 Spring Boot 3.2–3.5 兼容；XML Mapper + 注解 Mapper |
| 数据库 | MySQL Community | 8.0.45 | InnoDB、utf8mb4、严格 SQL 模式 |
| 缓存 | Redis | 3.2.100 | 按用户指定版本使用；此版本通常指旧版 Microsoft Open Tech Windows 移植包，见兼容说明 |
| Java Redis 客户端 | Spring Data Redis / Lettuce | Boot BOM 管理 | 不手动混用多个 Redis 客户端 |
| 管理后台 | Vue | 3.5.x | Composition API + TypeScript |
| 构建工具 | Vite | 8.1.x | 使用 npm lockfile 固定解析结果 |
| 管理后台组件库 | Element Plus | 2.14.7 | Vue 3 管理界面 |
| 小程序 | uni-app（Vue 3） | HBuilderX/CLI 稳定版 | 编译为微信小程序；记录实际 HBuilderX/CLI build 号 |
| 前端运行时 | Node.js | 24.18.0 | 固定此版本，Vite 8 的 Node 版本要求满足 |
| 包管理器 | npm | Node 24 随附版本 | 提交 `package-lock.json`，使用 `npm ci` 复现 |
| 路由 | Vue Router | 4.x | 管理后台 |
| 状态管理 | Pinia | 3.x | 管理后台；小程序仅在确有跨页共享状态时使用 |
| HTTP | Axios | 1.x | 统一封装 token、错误提示和超时 |
| API 文档 | springdoc-openapi | 2.x（Boot 3 兼容线） | 生成 OpenAPI/Swagger UI |
| Java 构建 | Maven | 3.9.x | 提交 Maven Wrapper，团队统一使用 Wrapper |
| 版本控制 | Git | 2.4x+ | GitHub 仓库启用分支与提交规范 |
| IDE | IntelliJ IDEA、VS Code/HBuilderX | 当前稳定版 | IDE 版本不影响构建结果 |
| 可选本地编排 | Docker Compose | Compose v2 | 可选；也可本地独立安装 MySQL/Redis |

**版本锁定规则**：按本节指定版本搭建，不自行替换 Java、MySQL、Redis、Node 版本。Spring Boot BOM 管理 Spring 依赖；禁止随意覆盖 BOM 版本。前端必须提交 lockfile。CI 或答辩环境应打印 Java、Node、Maven、MySQL、Redis 的实际版本。

**Redis 3.2.100 兼容提醒**：3.2.100 是旧版 Redis for Windows 移植发行包的版本号，不是当前 Redis 官方维护的稳定服务版本；其上游 Windows 仓库已归档。按用户指定版本仍以基础 `GET/SET/DEL/EXPIRE` 缓存功能为范围，不使用 Redis Streams、ACL、现代客户端专属命令或较新版本才有的语法。若 Spring Data Redis/Lettuce 与本机服务握手或命令兼容出现问题，优先检查连接配置与命令集；不得静默替换 Redis 版本，需由项目方确认后再变更。线上部署不应将该旧 Windows 移植版视为生产级 Redis 服务。

> 如学校电脑或实验室环境无法安装上述指定版本，先记录实际版本和兼容性问题，并与项目负责人确认后再调整本表；不要在代码、论文和部署环境中分别使用不同版本说明。

## 3. 总体架构

```text
患者微信小程序（uni-app/Vue 3）       Vue 3 管理后台
                    \                 /
                     HTTPS + JSON REST API
                                |
                   Spring Boot 3.5 单体服务
      Auth / User / Department / Doctor / Schedule /
             Appointment / Notice / Audit
                       |              |
                MySQL 8.0.45    Redis 3.2.100
             业务事实与事务       缓存/幂等辅助
```

- 前端只通过后端 API 访问业务数据，不直连 MySQL 或 Redis。
- 后端为模块化单体，使用 Controller、Service、Mapper、DTO/VO 分层。
- MySQL 是账号、排班、预约状态及号源的唯一事实来源。
- Redis 3.2.100 只做可丢失、可重建的数据缓存；不依赖其分布式锁特性，不能把 Redis 锁或缓存余量作为防超卖的唯一手段。
- 本地开发 CORS 白名单只允许本地前端地址；生产环境优先由 HTTPS 反向代理转发 API。

## 4. 角色、权限与业务规则

### 4.1 角色

| 角色编码 | 角色 | 权限边界 |
|---|---|---|
| `ADMIN` | 诊所管理员 | 管理科室、医生、排班、预约、公告、用户和日志 |
| `DOCTOR` | 医生 | 查看本人排班与预约；更新本人预约接诊状态 |
| `PATIENT` | 患者 | 浏览公开信息；维护本人就诊资料；管理本人预约 |

前端路由守卫只负责体验，所有授权必须由后端执行。患者标识从认证上下文读取，不接受客户端传入 `patient_id` 决定数据归属。

### 4.2 预约状态

第一版状态枚举：

| 值 | 含义 | 可进入状态 |
|---|---|---|
| `PENDING` | 患者已提交、待诊所确认 | `CONFIRMED`、`CANCELLED` |
| `CONFIRMED` | 诊所已确认 | `COMPLETED`、`CANCELLED`、`NO_SHOW` |
| `CANCELLED` | 已取消，终态 | 无 |
| `COMPLETED` | 已完成接诊，终态 | 无 |
| `NO_SHOW` | 爽约，终态 | 无 |

可以在实现时将自动确认设为默认策略：预约创建后直接为 `CONFIRMED`，但数据库仍保留 `PENDING` 以便扩展。前端显示中文状态，API 与数据库存储枚举英文值。

### 4.3 排班与预约规则

1. `doctor_schedule` 表的一行表示一个可预约时间段，而不是整天；例如医生在同一天上午和下午有两行。
2. 排班仅在 `PUBLISHED` 状态且未过预约截止时间时开放预约；停诊排班不允许新预约。
3. 预约创建必须满足：当前用户为患者、排班有效、时段未过、余量大于零、患者资料有效、同一患者不存在该排班的有效预约。
4. 同一患者同一排班最多一条有效预约（有效状态为 `PENDING`、`CONFIRMED`）。
5. 取消截止时间建议为 `start_time - cancel_before_minutes`，默认 120 分钟，由系统配置管理。已完成/爽约/已取消记录不能取消。
6. `total_slots` 为发布容量，`booked_slots` 为当前有效占用数，满足 `0 <= booked_slots <= total_slots`。排班已有预约时，不能将 `total_slots` 改到小于 `booked_slots`。
7. 医生、科室停用不删除历史预约。历史预约按预约创建时保存的快照字段展示。
8. 全部时间按诊所所在时区录入与展示（建议 `Asia/Shanghai`）；数据库连接统一使用 UTC 存取或统一使用 `DATETIME` + 应用层时区约定，严禁混用。本文建议数据库保存 UTC `DATETIME`，接口 ISO-8601 携带 `+08:00`。

## 5. 数据库设计

### 5.1 通用约定

- 数据库名：`dental_appointment`；字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`；所有业务表使用 InnoDB，运行版本为 MySQL 8.0.45。
- 表名、字段名使用小写 `snake_case`；主键使用 `BIGINT UNSIGNED` 自增 `id`。
- 时间字段使用 `DATETIME(3)`；应用以 UTC 保存，接口使用带偏移的 ISO-8601 字符串。
- 通用字段：**每张表**都必须包含 `created_at DATETIME(3) NOT NULL`（新增时间）和 `updated_at DATETIME(3) NOT NULL`（修改时间），并包含 `deleted TINYINT NOT NULL DEFAULT 0`（逻辑删除标记，0未删除、1已删除）。更新时间由应用统一维护；不可依赖各 SQL 写法隐式更新。
- 所有删除一律逻辑删除，禁止业务代码和管理接口执行物理 `DELETE`。普通查询默认追加 `deleted = 0`；历史预约、关联记录和审计日志如需隐藏也只置 `deleted = 1`，数据库行仍保留。预约完成/取消是状态迁移，不等同逻辑删除。
- 唯一约束须考虑软删除后的重建/复用规则。对允许“删除后重新创建同名数据”的字段，采用生成列（仅未删除行返回原值，已删除行为 NULL）建立唯一索引；对角色关联等可复用关联，优先恢复已删除旧行，避免唯一键冲突。
- 所有表不强制使用物理外键，使用服务层校验与索引维护关联；如学校要求外键，可在初版 SQL 加 FK，但必须保证删除策略不破坏历史预约。
- 密码使用 BCrypt 哈希，绝不保存明文。手机号是个人信息，后台列表默认脱敏。

### 5.2 表结构字典

#### `sys_user` — 登录账号

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK，自增 |
| `username` | VARCHAR(64) | NOT NULL，唯一，管理员/医生账号登录名 |
| `password_hash` | VARCHAR(100) | NOT NULL，BCrypt 摘要；微信绑定用户可用随机不可登录摘要 |
| `phone` | VARCHAR(20) | NULL，唯一（非空时）；按 E.164 或统一国内格式存储 |
| `wechat_openid` | VARCHAR(128) | NULL，唯一（非空时），微信登录扩展字段 |
| `display_name` | VARCHAR(80) | NOT NULL |
| `avatar_url` | VARCHAR(500) | NULL，仅保存受控 URL，不保存图片二进制 |
| `status` | TINYINT | NOT NULL DEFAULT 1；1启用、0停用 |
| `last_login_at` | DATETIME(3) | NULL |
| `deleted` | TINYINT | NOT NULL DEFAULT 0，逻辑删除 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL，新增/修改时间 |

软删除复用：为 `username`、`phone`、`wechat_openid` 建立 `active_username`、`active_phone`、`active_openid` 生成列（`CASE WHEN deleted=0 THEN 原字段 ELSE NULL END`）并对生成列建唯一索引；已删除账号的唯一值可重新注册。另为 `status, deleted` 建查询索引。

#### `sys_role` — 角色

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `role_code` | VARCHAR(32) | NOT NULL，唯一：`ADMIN`/`DOCTOR`/`PATIENT` |
| `role_name` | VARCHAR(64) | NOT NULL |
| `description` | VARCHAR(255) | NULL |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL |

#### `sys_user_role` — 用户角色关联

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `user_id` | BIGINT UNSIGNED | NOT NULL，关联 `sys_user.id` |
| `role_id` | BIGINT UNSIGNED | NOT NULL，关联 `sys_role.id` |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL |

约束：唯一索引 `uk_user_role(user_id, role_id)`；分别为 `user_id`、`role_id` 建索引。解绑时将关联行逻辑删除；再次绑定时恢复原行，不重复插入。

#### `patient_profile` — 患者/就诊人资料

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `user_id` | BIGINT UNSIGNED | NOT NULL，关联账号；允许多个就诊人 |
| `is_default` | TINYINT | NOT NULL DEFAULT 0，每个账号最多一位有效默认就诊人 |
| `real_name` | VARCHAR(80) | NOT NULL |
| `phone` | VARCHAR(20) | NOT NULL |
| `gender` | TINYINT | NULL；0未知、1男、2女 |
| `birth_date` | DATE | NULL；非必要不收集 |
| `remark` | VARCHAR(255) | NULL，非医疗诊断字段 |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL |

V4 迁移移除 `uk_patient_user(user_id)`，增加 `idx_patient_user(user_id,deleted,id)` 与生成列 `active_default_user_id` 的唯一索引。一个账号可维护多位就诊人，删除仅标记 `deleted=1`；默认就诊人最多一位。第一版不采集身份证号。

#### `department` — 科室

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `name` | VARCHAR(80) | NOT NULL，唯一（未删除范围内由应用校验） |
| `description` | VARCHAR(1000) | NULL |
| `sort_order` | INT | NOT NULL DEFAULT 0 |
| `status` | TINYINT | NOT NULL DEFAULT 1；1启用、0停用 |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL |

索引：`idx_department_status_sort(status, deleted, sort_order)`；新增科室前检查同名未删除记录，删除后再建同名科室时优先恢复旧记录。

#### `doctor` — 医生档案

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `user_id` | BIGINT UNSIGNED | NULL，关联医生登录账号；启用医生账号时唯一 |
| `department_id` | BIGINT UNSIGNED | NOT NULL |
| `name` | VARCHAR(80) | NOT NULL |
| `title` | VARCHAR(80) | NULL，职称 |
| `specialty` | VARCHAR(500) | NULL，擅长方向 |
| `introduction` | TEXT | NULL |
| `avatar_url` | VARCHAR(500) | NULL |
| `status` | TINYINT | NOT NULL DEFAULT 1 |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL |

索引：`idx_doctor_department_status(department_id, status, deleted)`；`uk_doctor_user(user_id)`。医生档案逻辑删除后恢复原行，避免与唯一医生账号关联冲突。

#### `doctor_schedule` — 医生号源时段

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `doctor_id` | BIGINT UNSIGNED | NOT NULL |
| `department_id` | BIGINT UNSIGNED | NOT NULL，排班创建时写入，便于查询 |
| `start_time` | DATETIME(3) | NOT NULL，UTC |
| `end_time` | DATETIME(3) | NOT NULL，UTC，必须大于开始时间 |
| `total_slots` | INT UNSIGNED | NOT NULL，必须大于0 |
| `booked_slots` | INT UNSIGNED | NOT NULL DEFAULT 0 |
| `status` | VARCHAR(16) | NOT NULL：`DRAFT`/`PUBLISHED`/`CLOSED`/`CANCELLED` |
| `cancel_before_minutes` | SMALLINT UNSIGNED | NOT NULL DEFAULT 120 |
| `version` | INT UNSIGNED | NOT NULL DEFAULT 0，乐观锁备用 |
| `created_by` | BIGINT UNSIGNED | NOT NULL，管理员 user id |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL |

约束与索引：`CHECK (end_time > start_time)`、`CHECK (booked_slots <= total_slots)`；唯一 `uk_doctor_schedule_period(doctor_id, start_time, end_time)`；查询索引 `idx_schedule_search(department_id, status, start_time, deleted)`、`idx_schedule_doctor(doctor_id, start_time, deleted)`。排班容量变更要在事务中校验 `total_slots >= booked_slots`。相同医生/时段被逻辑删除后再次排班时恢复原行，避免唯一键冲突。

#### `appointment` — 预约单

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `appointment_no` | VARCHAR(32) | NOT NULL，唯一；后端生成，不使用连续自增号作为业务编号 |
| `patient_user_id` | BIGINT UNSIGNED | NOT NULL，当前预约患者账号 |
| `patient_profile_id` | BIGINT UNSIGNED | NOT NULL，就诊人资料 |
| `schedule_id` | BIGINT UNSIGNED | NOT NULL |
| `doctor_id` | BIGINT UNSIGNED | NOT NULL，冗余用于历史检索 |
| `department_id` | BIGINT UNSIGNED | NOT NULL，冗余用于历史检索 |
| `patient_name_snapshot` | VARCHAR(80) | NOT NULL，预约时姓名快照 |
| `patient_phone_snapshot` | VARCHAR(20) | NOT NULL，预约时手机号快照 |
| `doctor_name_snapshot` | VARCHAR(80) | NOT NULL |
| `department_name_snapshot` | VARCHAR(80) | NOT NULL |
| `start_time_snapshot` | DATETIME(3) | NOT NULL，UTC |
| `end_time_snapshot` | DATETIME(3) | NOT NULL，UTC |
| `chief_complaint` | VARCHAR(500) | NULL，患者主动填写的简短主诉；不作为诊断 |
| `status` | VARCHAR(16) | NOT NULL，预约状态枚举 |
| `appointment_active_key` | VARCHAR(80) | NULL，有效预约为 `patientUserId:scheduleId`，无效状态/逻辑删除时置 NULL |
| `cancel_reason` | VARCHAR(255) | NULL |
| `cancelled_at` | DATETIME(3) | NULL |
| `handled_by` | BIGINT UNSIGNED | NULL，后台操作人 |
| `handled_at` | DATETIME(3) | NULL |
| `deleted` | TINYINT | NOT NULL DEFAULT 0；预约禁止物理删除，需要隐藏时只逻辑删除 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL |

索引：唯一 `uk_appointment_no(appointment_no)`；`idx_appointment_patient_status(patient_user_id, status, created_at, deleted)`；`idx_appointment_schedule_status(schedule_id, status, deleted)`；`idx_appointment_doctor_time(doctor_id, start_time_snapshot)`。禁止物理删除预约记录。

重复预约约束：MySQL 不支持基于状态的部分唯一索引。有效预约写入 `appointment_active_key=patientUserId:scheduleId` 并建立唯一索引 `uk_appointment_active_key(appointment_active_key)`；取消/完成/爽约或管理员逻辑删除预约时置 NULL。状态、软删除标记、有效键和排班号源必须在同一事务内维护。若同一有效预约被逻辑删除，需同步释放该预约占用的号源；只隐藏而不改变预约业务状态时不得逻辑删除该行，避免预约状态与余量对不上。

#### `clinic_notice` — 诊所公告

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `title` | VARCHAR(120) | NOT NULL |
| `content` | TEXT | NOT NULL |
| `status` | TINYINT | NOT NULL DEFAULT 0；0草稿、1发布、2撤回 |
| `publish_at` | DATETIME(3) | NULL |
| `expire_at` | DATETIME(3) | NULL |
| `created_by` | BIGINT UNSIGNED | NOT NULL |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL |

索引：`idx_notice_public(status, publish_at, expire_at)`。

#### `operation_log` — 管理操作日志

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `operator_user_id` | BIGINT UNSIGNED | NULL，系统任务时为空 |
| `operator_role` | VARCHAR(32) | NULL |
| `action` | VARCHAR(64) | NOT NULL，如 `SCHEDULE_PUBLISH` |
| `target_type` | VARCHAR(64) | NOT NULL |
| `target_id` | VARCHAR(64) | NULL |
| `summary` | VARCHAR(500) | NULL，不存密码、token、完整主诉等敏感值 |
| `ip_address` | VARCHAR(45) | NULL，IPv4/IPv6 |
| `deleted` | TINYINT | NOT NULL DEFAULT 0；日志只追加，若依法需清理也仅逻辑删除 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL |

索引：`idx_log_operator_time(operator_user_id, created_at)`、`idx_log_target(target_type, target_id)`。只允许追加，不提供普通管理员删除接口。

#### `system_config` — 系统配置（建议纳入首版）

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | BIGINT UNSIGNED | PK |
| `config_key` | VARCHAR(100) | NOT NULL，唯一 |
| `config_value` | VARCHAR(500) | NOT NULL；不保存密钥/密码 |
| `description` | VARCHAR(255) | NULL |
| `updated_by` | BIGINT UNSIGNED | NULL |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 |
| `created_at` / `updated_at` | DATETIME(3) | NOT NULL |

初始键：`clinic.name`、`clinic.phone`、`clinic.address`、`appointment.cancel_before_minutes`。密钥等敏感配置只能通过环境变量注入。

### 5.3 初始化数据

数据库迁移脚本必须初始化 `ADMIN`、`DOCTOR`、`PATIENT` 三个角色及系统配置。可以提供本地演示管理员，但密码必须首次启动时通过环境变量或初始化流程设置 BCrypt 哈希；禁止在仓库提交可直接登录的生产默认密码。提供 2–3 个虚构科室、医生和未来排班作为演示数据，标注为开发环境 seed，不得使用真实患者数据。

### 5.4 ER 关系概要

```text
sys_user 1──1 patient_profile
sys_user N──M sys_role（经 sys_user_role）
department 1──N doctor
doctor 1──N doctor_schedule
patient_profile 1──N appointment
doctor_schedule 1──N appointment
clinic_notice、operation_log、system_config 为独立支撑表
```

## 6. 后端项目结构

建议仓库结构：

```text
dental-appointment/
├─ backend/
│  ├─ pom.xml
│  └─ src/main/java/com/example/dental/
│     ├─ DentalAppointmentApplication.java
│     ├─ common/          # R<T>、PageResult、异常、常量、枚举
│     ├─ config/          # Security、Redis、Jackson、CORS、OpenAPI
│     ├─ security/        # token filter、当前用户、权限注解
│     ├─ auth/            # 登录、令牌刷新/失效
│     ├─ user/            # 账号与患者资料
│     ├─ department/      # 科室
│     ├─ doctor/          # 医生
│     ├─ schedule/        # 排班与号源
│     ├─ appointment/     # 预约创建/取消/处理
│     ├─ notice/          # 公告
│     └─ audit/           # 操作日志
│  └─ src/main/resources/
│     ├─ application.yml
│     ├─ mapper/*.xml
│     └─ db/migration/    # Flyway SQL
├─ admin-web/             # Vue 3 + TypeScript 管理后台
├─ miniapp/               # uni-app + Vue 3 微信小程序
├─ deploy/                # 可选 compose、反向代理示例
├─ docs/                  # OpenAPI 导出、部署与用户手册
└─ README.md
```

业务模块内部建议统一结构：`controller/`、`service/`、`mapper/`、`entity/`、`dto/`、`vo/`。跨模块调用只依赖公开 Service/DTO，不直接访问其他模块的 Mapper。对于毕业设计的工程规模，不引入微服务或过度抽象。

## 7. API 约定

### 7.1 通用约定

- API 前缀：`/api/v1`；管理端专用接口使用 `/api/v1/admin/...`。
- JSON 字段使用 `camelCase`；数据库字段保持 `snake_case`。
- 前后端之间所有 JSON 请求体和响应体统一使用 `R<T>` 泛型包装。请求体的业务参数放在 `data` 字段中；请求中的 `code/message` 不作为可信输入（允许省略或置空），`traceId` 由服务端生成或从请求追踪头取得。URL 路径、Query、HTTP Header 仍按 HTTP 语义传递，不额外塞进 JSON。
- 分页参数：`page` 从 1 开始，`size` 默认 10、最大 100；排序字段采用白名单，禁止客户端直接传 SQL 字段。
- **所有前后端 API 响应体均使用泛型包装类 `R<T>` 包装**；成功与业务错误保持相同 JSON 结构，不允许接口裸返回对象。
- `R<T>` 最小字段：`code`（业务码）、`message`（用户可读信息）、`data`（泛型业务数据，可空）、`traceId`（请求追踪号）。可增加 `success` 布尔字段，但全项目必须一致。HTTP 状态码仍正确使用 200/201/400/401/403/404/409/500；包装类不取代 HTTP 语义。
- 成功响应示例：`{"code":"OK","message":"success","data":{"appointmentNo":"AP202610050001","status":"CONFIRMED"},"traceId":"..."}`。
- 错误响应示例：`{"code":"APPOINTMENT_FULL","message":"该时段号源已满","data":null,"traceId":"..."}`。
- 分页响应也放在 `R<T>` 的 `data` 中，例如 `R<PageResult<AppointmentListVO>>`，不要另发明分页外层结构。
- HTTP 状态码表达通用结果（200/201/400/401/403/404/409/500）；业务码表达具体失败原因。
- 金额字段本期不存在。时间字符串格式为 ISO-8601，例如 `2026-10-06T09:00:00+08:00`。
- 所有写接口验证请求 DTO，限制长度和枚举；错误不返回堆栈或 SQL。

### 7.2 患者/公开接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | 公开 | 账号密码登录；小程序微信登录适配器可后续补充 |
| `POST` | `/api/v1/auth/logout` | 已登录 | 注销当前令牌/加入失效列表 |
| `GET` | `/api/v1/clinic` | 公开 | 诊所公开信息 |
| `GET` | `/api/v1/departments` | 公开 | 已启用科室，分页可选 |
| `GET` | `/api/v1/doctors` | 公开 | 支持 `departmentId`、`keyword`、`page`、`size` |
| `GET` | `/api/v1/doctors/{doctorId}` | 公开 | 医生详情 |
| `GET` | `/api/v1/schedules` | 公开 | 支持 `doctorId`、`departmentId`、`dateFrom`、`dateTo`；只返回已发布可约数据 |
| `GET` | `/api/v1/notices` | 公开 | 当前有效公告 |
| `GET` | `/api/v1/me` | PATIENT/DOCTOR/ADMIN | 当前账号资料 |
| `PUT` | `/api/v1/me/patient-profile` | PATIENT | 更新本人默认就诊人资料 |
| `PUT` | `/api/v1/me/account-profile` | PATIENT | 更新本人账号昵称 |
| `GET/POST` | `/api/v1/me/patient-profiles` | PATIENT | 列出或新增本人账号的就诊人 |
| `PUT/DELETE` | `/api/v1/me/patient-profiles/{id}` | PATIENT | 更新或逻辑删除指定就诊人 |
| `PUT` | `/api/v1/me/patient-profiles/{id}/default` | PATIENT | 设置默认就诊人 |
| `POST` | `/api/v1/appointments` | PATIENT | 创建预约；返回预约编号和状态 |
| `GET` | `/api/v1/appointments/me` | PATIENT | 查询本人预约，支持状态/日期分页 |
| `GET` | `/api/v1/appointments/{id}` | 预约本人或有权限员工 | 预约详情 |
| `POST` | `/api/v1/appointments/{id}/cancel` | PATIENT/ADMIN | 患者只能取消本人预约；管理员取消需记录原因 |

创建预约请求示例：

```json
{
  "data": {
    "scheduleId": 10001,
    "patientProfileId": 501,
    "chiefComplaint": "牙齿遇冷不适"
  }
}
```

患者账号不能在请求中指定 `patientUserId`、`doctorId` 或价格；医生与科室由 `scheduleId` 在服务端读取。接口支持可选 `Idempotency-Key` 请求头，避免网络重试重复建单。

Java 响应模型统一为 `R<T>`，示意：

```java
public class R<T> {
    private String code;
    private String message;
    @Valid
    private T data;
    private String traceId;
}
```

Controller 写接口接收 `R<AppointmentCreateDTO>`、`R<DepartmentSaveDTO>` 等请求对象，并从 `.data` 取经过 `@Valid` 级联校验的 DTO；请求 `code/message` 忽略且不作为可信值。所有方法返回 `R<LoginVO>`、`R<AppointmentVO>` 或 `R<PageResult<...>>` 等类型。统一异常处理器也转换为 `R<?>`，同时设置恰当 HTTP 状态码。登录凭证也放在 `R<LoginVO>.data` 中返回。前端定义同名泛型 TypeScript 类型 `R<T>`，API 客户端统一负责请求包装和响应解包。

### 7.3 管理接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| `GET/POST/PUT` | `/api/v1/admin/departments` | ADMIN | 科室列表、新增、编辑/停用 |
| `GET/POST/PUT` | `/api/v1/admin/doctors` | ADMIN | 医生列表、新增、编辑/停用 |
| `GET/POST/PUT` | `/api/v1/admin/schedules` | ADMIN | 排班草稿、编辑、发布、停诊 |
| `GET` | `/api/v1/admin/appointments` | ADMIN/DOCTOR（范围受限） | 按日期、状态、医生、科室筛选 |
| `PATCH` | `/api/v1/admin/appointments/{id}/status` | ADMIN/对应 DOCTOR | 确认、完成、爽约等合法状态转换 |
| `GET/POST/PUT` | `/api/v1/admin/notices` | ADMIN | 公告草稿、发布、撤回 |
| `GET` | `/api/v1/admin/dashboard` | ADMIN | 预约统计、今日接诊概览 |
| `GET` | `/api/v1/admin/operation-logs` | ADMIN | 只读分页查询 |
| `GET/PUT` | `/api/v1/admin/system-config` | ADMIN | 诊所和预约规则设置 |

`PUT` 用于整体替换可编辑资源；`PATCH` 用于部分更新或状态迁移。任何状态迁移必须经过 Service 中的状态机校验，不能直接把任意字符串写进数据库。

## 8. 关键业务实现细节

### 8.1 创建预约事务

推荐数据库条件更新方案，降低锁复杂度。一个事务内依次完成：

1. 从认证上下文取得 `patient_user_id`，验证就诊人资料属于该用户。
2. 对排班执行条件扣减：仅当 `status='PUBLISHED'`、`start_time` 未到、`booked_slots < total_slots` 时执行 `booked_slots = booked_slots + 1`；检查受影响行数必须为 1。
3. 使用有效预约唯一键 `patientId:scheduleId` 插入预约，并保存医生、科室、就诊人、时段快照。
4. 插入失败（包括唯一键冲突）时事务回滚，号源扣减随之回滚；将数据库冲突映射为 `DUPLICATE_APPOINTMENT`。
5. 返回生成的预约编号。

Mapper 条件更新语义（最终 SQL 由 Mapper XML 实现）：

```sql
UPDATE doctor_schedule
SET booked_slots = booked_slots + 1,
    updated_at = UTC_TIMESTAMP(3)
WHERE id = #{scheduleId}
  AND status = 'PUBLISHED'
  AND start_time > UTC_TIMESTAMP(3)
  AND booked_slots < total_slots;
```

唯一键冲突时必须回滚整个事务。禁止先查余量再无锁插入，也禁止由前端传 `booked_slots`。

### 8.2 取消预约事务

1. 使用 `SELECT ... FOR UPDATE` 锁定预约记录并验证身份、当前状态和取消截止时间。
2. 将预约从 `PENDING/CONFIRMED` 改为 `CANCELLED`，填写取消时间、原因，并将 `appointment_active_key` 置 NULL。
3. 在同一事务内对排班 `booked_slots` 做 `GREATEST(booked_slots - 1, 0)` 或先校验大于零后扣减。
4. 只有第一次成功从有效状态迁移到取消状态才释放号源；重复取消返回 `APPOINTMENT_NOT_CANCELLABLE`，不再次扣减。

### 8.3 Redis 使用策略

- 可缓存公开科室、医生简介及当天排班查询；设置短 TTL，并在后台发布/修改排班后删除相应缓存键。
- 缓存键建议带版本前缀，例如 `dental:v1:doctor:{id}`、`dental:v1:schedules:{date}:{departmentId}`。
- 不缓存个人预约敏感数据；如确需缓存，必须按用户隔离并设置短 TTL。
- 不将缓存结果作为权限判断依据。Redis 故障时公开查询可降级直查 MySQL；预约创建仍以 MySQL 事务决定成功或失败。
- 可使用 Redis 幂等键减少重复请求，但过期、丢失或 Redis 不可用时，数据库唯一约束仍须阻止重复有效预约。

## 9. 认证、安全与隐私

- 管理端账号密码登录；患者第一版可用账号密码/测试账号登录，微信 `code2session` 作为下一阶段接入。不能把微信 `openid` 当作前端可控的用户身份。
- 使用 Spring Security + JWT（建议短时 access token；刷新令牌方案根据工作量选择）。JWT secret 由环境变量注入，禁止写入源码、SQL、README 或前端包。
- 密码使用 BCrypt；登录错误对外统一为“账号或密码错误”，避免枚举用户；可加入简单的失败次数限流。
- 所有管理接口要求 `ADMIN` 或 `DOCTOR` 角色；医生查询与操作须再校验 `doctor.user_id == currentUser.id`。
- 防越权：通过认证用户 ID 查询本人资源，不依赖 URL 中的 ID 代表所有权。
- 防注入：MyBatis 使用 `#{}` 参数绑定；动态排序、表名等采用后端白名单，严禁 `${}` 接收用户输入。
- CORS 使用明确的本地/生产域名白名单；生产启用 HTTPS；限制请求大小及主诉长度。
- 日志不得记录 Authorization、密码、微信密钥、完整手机号或患者主诉。展示手机号默认 `138****1234` 样式脱敏。
- 只采集预约必需的信息；不采集身份证号码、支付信息或诊断数据。演示数据必须虚构。
- 若用于真实诊所，须另行评估个人信息保护、数据备份、访问审计和部署安全要求；毕业设计原型不宣称满足医疗生产系统合规认证。

## 10. 前端页面与路由

### 10.1 微信小程序

底部导航建议：`首页`、`预约`、`我的`。

| 页面 | 页面路径 | 核心内容 |
|---|---|---|
| 首页 | `pages/home/index` | 诊所信息、公告、热门科室/医生、预约入口 |
| 科室列表 | `pages/department/index` | 可预约科室 |
| 医生列表 | `pages/doctor/list` | 科室筛选、关键词搜索 |
| 医生详情 | `pages/doctor/detail` | 简介、擅长、未来排班 |
| 选择时段 | `pages/schedule/index` | 日期、时段、剩余号源 |
| 确认预约 | `pages/appointment/confirm` | 就诊人、主诉、预约须知、提交 |
| 预约结果 | `pages/appointment/result` | 预约编号、医生和就诊时间 |
| 我的预约 | `pages/appointment/list` | 状态筛选、详情与取消 |
| 预约详情 | `pages/appointment/detail` | 预约快照、状态、取消操作 |
| 个人中心 | `pages/profile/index` | 登录状态、资料维护、诊所联系信息 |
| 登录 | `pages/auth/login` | 原型账号登录；微信登录入口可配置 |

所有页面需覆盖加载中、空列表、请求失败、无权限和号源已满状态；表单点击提交后防止重复触发，但后端仍需保证幂等与并发安全。

### 10.2 Vue 管理后台

| 菜单 | 页面功能 |
|---|---|
| 工作台 | 今日预约数、待处理数、近期预约趋势和快捷入口 |
| 科室管理 | 科室列表、搜索、新增/编辑、启停 |
| 医生管理 | 医生列表、科室/职称筛选、账号关联、新增/编辑、停用 |
| 排班管理 | 按日期/医生查询；新建草稿、编辑号源、发布、停诊 |
| 预约管理 | 日期/科室/医生/状态筛选；预约详情、确认、完成、爽约 |
| 公告管理 | 编辑草稿、发布、撤回、有效期设置 |
| 患者资料 | 按需查询最少必要信息，手机号脱敏 |
| 操作日志 | 按操作者、动作、时间筛选，只读 |
| 系统设置 | 诊所公开信息、取消截止时间 |
| 登录与个人设置 | 登录、退出、修改密码 |

管理表单统一使用服务端校验结果；删除基础资料优先改为停用，不硬删被预约引用的医生或科室。

## 11. 编码规范

### 11.0 总体依据

Java 后端遵循《阿里巴巴 Java 开发手册（黄山版，v1.7.1）》的强制规约，并参考其推荐规约；发生冲突时，以本项目已明确的接口、数据、权限要求为准。规范覆盖命名与格式、异常与日志、MySQL/SQL、分层工程、安全与测试。代码评审检查不得使用魔法值、不得吞异常、日志不得泄露敏感信息、SQL 参数必须绑定、事务和并发操作必须有明确边界。可在 IDEA 安装 Alibaba Java Coding Guidelines 插件并使用黄山版规则集辅助检查。

### 11.1 Java

- 包名全小写；类名 `UpperCamelCase`，方法/字段 `lowerCamelCase`，常量 `UPPER_SNAKE_CASE`。
- Controller 命名为 `XxxController`，Service 接口/实现按团队选择一种风格并全项目统一，Mapper 命名 `XxxMapper`。
- 使用构造器注入；禁止字段注入 `@Autowired`。
- Controller 只处理 HTTP、DTO 校验和响应，不写 SQL 或事务业务逻辑。
- 事务边界放在 Service 公共业务方法；预约创建/取消显式 `@Transactional(rollbackFor = Exception.class)`。
- Entity、请求 DTO、响应 VO 分离；禁止直接返回数据库实体或接收 Entity 作为请求体。
- 金额/精确小数（如未来收费）使用 `BigDecimal`；当前不实现支付。
- 枚举集中定义合法状态值；禁止用任意字符串跨层传递状态。
- 统一异常：参数错误、未认证、无权限、资源不存在、业务冲突分别映射 400/401/403/404/409。
- Controller 成功响应必须使用 `R<T>`；分页使用 `R<PageResult<T>>`；异常处理统一生成 `R<?>`，不得返回裸字符串或框架默认错误 JSON。
- 所有 JSON 请求体也以 `R<T>` 包装，Controller 从 `R<T>.data` 读取实际 DTO；前端 API 层负责统一装包，页面组件不自行拼接外层结构。URL Query、Path、Header 不属于 JSON 请求体包装范围。
- 禁止空 catch，禁止捕获异常后只打印日志；异常须转为明确业务错误或继续抛出。
- 集合可预估容量时指定初始容量；字符串比较使用 `Objects.equals` 或常量在前；`BigDecimal` 使用字符串构造，避免 `new BigDecimal(double)`。
- 禁止使用 `Executors` 创建不可控线程池；如后续加入异步任务，使用有界线程池并明确拒绝策略（首版不启用异步）。
- 日志使用参数化占位符，按级别记录；禁止记录密码、完整手机号、令牌、密钥或完整主诉。
- Mapper 所有 SQL 参数化；复杂 SQL 放 XML 并在变更时检查索引和查询条件。
- 禁止 `System.out.println`；使用 SLF4J 参数化日志，避免拼接敏感信息。
- 使用 `@Valid`、Jakarta Bean Validation；数据库约束作为最后一道一致性保护。

### 11.2 TypeScript / Vue

- TypeScript 开启 `strict: true`；避免 `any`，跨 API 数据定义明确 interface/type。
- Vue 组件使用 `<script setup lang="ts">` 与 Composition API；组件名 `PascalCase.vue`，变量/方法 `camelCase`。
- 页面组件只组合交互，API 请求放入 `src/api/`，可复用业务逻辑放入 `src/composables/`。
- Axios 统一设置 base URL、超时、认证头、错误码处理；页面不得散落重复请求配置。
- 路由权限在前端隐藏无权限入口，但后端必须再次授权。
- 日期显示通过统一工具按 `Asia/Shanghai` 格式化；不要依赖浏览器默认时区解析无时区日期。
- 组件表单包含 loading、校验错误、提交成功/失败提示；危险操作有明确二次确认。
- 格式化采用项目统一 Prettier/ESLint 配置；不在同一提交混入大规模无关格式化。

### 11.3 SQL 与迁移

- 表/字段小写下划线；所有表必须有主键、时间字段和必要索引。
- 使用 Flyway 管理迁移：`V1__create_core_tables.sql`、`V2__seed_roles_and_config.sql` 等；已执行迁移不可原地修改，新增版本修正。
- 字符串字段选择合理长度；避免无边界 `TEXT`；时间统一 UTC 约定。
- 对查询列表先确定筛选条件，再建立组合索引；索引顺序遵循常用等值条件、范围条件和排序需求。
- 预约表保留历史快照；被引用基础资料停用而非删除。
- DDL 中的约束与 Service 校验必须一致；如 MySQL 环境不执行某些 CHECK，Service 仍需校验。
- 全库禁止物理 `DELETE`（包括关系表和账号）；删除操作统一为 `UPDATE ... SET deleted = 1, updated_at = ...`。应用 CRUD 不提供永久删除，特殊运维清理另行审批并留存审计。
- 所有表均有 `deleted`、`created_at`、`updated_at` 三个公共字段；新增时初始化创建/修改时间，任何更新（含逻辑删除）都刷新 `updated_at`。
- 所有常规 Mapper 查询默认过滤 `deleted = 0`；需查历史/已删除行时使用明确命名的方法，避免遗漏过滤条件。
- 软删除行须按唯一值复用规则处理；允许复用的唯一键用“有效记录生成列 + 唯一索引”，不能简单把 `deleted` 加入唯一索引而阻止重复创建。

### 11.4 Git 与提交

- 分支：`main` 保持可运行；开发使用 `feat/<name>`、`fix/<name>`、`docs/<name>`。
- 提交格式：`feat(appointment): add booking endpoint`、`fix(schedule): prevent slot overbooking`、`docs: update setup guide`。
- 一次提交围绕一个可说明的改动；提交前确认不包含 `.env`、私钥、IDE 缓存、构建产物和真实个人数据。
- PR/合并说明包含目的、影响范围、数据库迁移、手工验证结果及截图（前端变更时）。
- `.gitignore` 至少排除 `target/`、`node_modules/`、`dist/`、`.env*`（保留 `.env.example`）、IDE 私有配置、日志和本地数据库数据目录。

## 12. 配置、运行与部署约定

配置分为 `application.yml` 默认项和 `application-local.yml` 本地项，敏感值只从环境变量读取。仓库只提交 `.env.example`，变量包括：

```text
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8080
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=dental_appointment
DB_USERNAME=dental_app
DB_PASSWORD=本地自设
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=本地自设或空
JWT_SECRET=至少32字节随机值
```

数据库连接池由 Spring Boot 管理（HikariCP）；连接字符集、时区和池上限统一写在后端配置。开发跨域只允许 `http://localhost:5173` 和小程序开发工具所需来源；生产由 Nginx/Caddy 等 HTTPS 反代到 8080。

本地启动顺序：

1. 安装 Java 17、Node.js 24.18.0、Maven 3.9.x、MySQL 8.0.45、Redis 3.2.100（Windows 移植版，仅开发/演示环境）及微信开发者工具。
2. 创建数据库和最小权限数据库账号；复制 `.env.example` 为本地配置并设置密码/密钥。
3. 启动 MySQL、Redis；后端通过 Flyway 自动建表和写入基础角色。
4. 启动后端；通过 `/actuator/health` 或项目健康接口确认服务可用。
5. 在 `admin-web` 执行 `npm ci`、`npm run dev`，浏览器访问 Vite 地址。
6. 在 `miniapp` 安装依赖并运行到微信开发者工具；填写后端 API base URL。
7. 按 README 演示账号流程完成管理员建科室/医生/排班，再以患者身份预约。

提交前补齐 README：先决条件、端口、环境变量、数据库初始化、管理员初始化、安全提醒、管理端与小程序启动步骤及常见错误。

## 13. 开发顺序与阶段性交付

| 阶段 | 实现范围 | 可检查成果 |
|---|---|---|
| 0. 工程骨架 | Git 仓库、后端 Maven、Vue 后台、uni-app 小程序、统一配置 | 三端可启动，README 初稿 |
| 1. 数据与认证 | Flyway 表、角色、登录、JWT、当前用户接口 | 管理员/患者登录及权限边界 |
| 2. 基础资料 | 科室、医生增改查和启停 | 后台可维护，小程序可查询 |
| 3. 排班 | 草稿、发布、修改、停诊、查询余量 | 管理端排班完整闭环 |
| 4. 预约核心 | 预约创建、幂等/唯一约束、取消、患者预约列表 | 端到端预约及号源守恒 |
| 5. 后台接诊 | 筛选、确认、完成、爽约、医生权限 | 管理端业务闭环 |
| 6. 收尾 | 公告、仪表盘、操作日志、错误提示、部署文档 | 可演示交付版本 |

先打通最小纵向链路（登录→排班→预约→后台查询），再并行补齐列表页面，不要一开始先把所有 UI 页面做完再开发后端。

## 14. 质量检查清单

- 数据库迁移可在空数据库执行，二次启动不重复插入角色或演示数据。
- 预约容量为 1 时多个并发请求只能成功 1 个；失败请求不留预约记录、也不改变余量。
- 同一患者重复预约失败；另一个患者仍可预约余量。
- 取消事务成功后号源恰好增加 1；重复取消不会再次加号。
- 排班停诊/过期/满额时新预约返回清楚的业务错误码。
- 患者尝试访问他人预约、医生访问其他医生数据均被拒绝。
- Redis 清空或短时不可用不会导致重复预约或号源超卖。
- 管理后台可按日期、医生、科室、状态筛选预约，分页稳定。
- Web 与小程序遇到 401、403、409、网络超时时有清晰提示。
- 仓库无明文密码/密钥、个人数据、构建目录或数据库运行文件。

## 15. GitHub 参考范围与使用原则

公开仓库检索到的相近项目可用来对照功能闭环和工程组织，不作为可直接复制的代码来源。检索结果包括：

- [yyzwz/order-register](https://github.com/yyzwz/order-register)：包含科室/医生、放号、预约和我的挂号，技术版本较早，可参考业务功能覆盖。
- [AbsoluteZero001/springboot-vaccine-appointment-system](https://github.com/AbsoluteZero001/springboot-vaccine-appointment-system)：采用 Vue 3、Spring Boot 3、MySQL、Redis 的预约库存模式，可对照前后端分离和容器化组织。
- [giteecode/hospitalRegistePublic](https://github.com/giteecode/hospitalRegistePublic)：明确包含微信小程序、管理后台、医生排班与预约等客户端/模块划分，可用于核对小程序用户流程。
- [xdmdcp/hospital](https://github.com/xdmdcp/hospital)：展示小程序与 Spring Boot/MyBatis 分离的项目形态，可作为端侧结构参考。

参考原则：只借鉴公开的功能分类、目录思想和常见工程实践；逐文件核对许可证；不复制仓库源码、数据库或论文文本来冒充独立成果；第三方依赖遵循各自许可证。GitHub 插件安装并授权后，如有具体仓库，可再核对它的 README、许可证、最新分支和代码结构。

## 16. 文献与论文材料说明

知网尚未连接，本设计文档不伪造知网检索结果、作者或文献结论。开题报告的研究现状与参考文献需要另行在知网按“微信小程序 预约挂号”“口腔诊所 信息系统”“Spring Boot 医疗预约 系统设计”等关键词检索，记录作者、题名、来源、年份、卷期页码和 DOI/数据库链接后再引用。技术选型的版本依据应引用各工具官方文档，不用毕业设计论文替代软件官方发布说明。

## 17. 官方版本依据与资料链接

- [Spring Boot 官方项目页及稳定版本](https://spring.io/projects/spring-boot)
- [MyBatis Spring Boot Starter 版本兼容表](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
- [Spring Boot 3.5 系统要求（Java 17+）](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Oracle Java SE 17 文档](https://docs.oracle.com/en/java/javase/17/)
- [Node.js 发布与 LTS 状态](https://nodejs.org/en/about/previous-releases)
- [Node.js 24.18.0 官方下载档案](https://nodejs.org/en/download/archive/v24.18.0)
- [Vite 8 发布说明与 Node 版本要求](https://vite.dev/blog/announcing-vite8)
- [MySQL 8.0.45 官方发布说明](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/news-8-0-45.html)
- [Redis for Windows 3.2.100 版本背景](https://redis.io/blog/redis-on-windows-8-1-and-previous-versions/)
- [《阿里巴巴 Java 开发手册》公开仓库](https://github.com/alibaba/Alibaba-Java-Coding-Guidelines)
- [Element Plus 官方发布页](https://github.com/element-plus/element-plus/releases)
- [Vue 官方文档](https://vuejs.org/)
- [uni-app 官方文档](https://uniapp.dcloud.net.cn/)

---

## 附录 A：第一版开发任务拆分（可直接转为 issue）

1. 初始化 monorepo 目录、后端启动类、前端应用、环境模板和 README。
2. 编写 Flyway `V1`：用户、角色、关联、患者资料、科室、医生、排班、预约、公告、日志、配置表及索引。
3. 编写认证：密码哈希、登录、JWT 解析过滤器、角色鉴权、当前用户上下文、退出/失效策略。
4. 实现科室后台 CRUD/停用与公开查询。
5. 实现医生后台 CRUD/停用、医生账号关联与医生公开详情。
6. 实现排班草稿创建、合法性校验、发布/停诊、修改容量与公开余量查询。
7. 实现预约创建事务、业务编号、有效预约唯一键、幂等头及错误码映射。
8. 实现患者预约列表/详情/取消；实现后台预约检索及状态流转。
9. 实现小程序首页、医生浏览、排班选择、预约确认、结果、我的预约。
10. 实现管理后台登录、工作台、科室/医生/排班/预约/公告/日志页面。
11. 为排班读查询加入 Redis 缓存和后台变更后的定向失效；预约写流程仍由 MySQL 事务保障。
12. 整理部署脚本、虚构演示数据、用户操作步骤、论文所需架构图与 ER 图。

## 附录 B：需在实际开发时确认的可配置项

以下项不阻塞初版实现，按默认值开发，并允许后台配置或后续调整：

- 是否所有预约自动确认为 `CONFIRMED`（默认：管理员确认）。
- 取消截止时间（默认：开始前 120 分钟）。
- 号源按固定时段还是诊疗项目时长生成（默认：管理员直接创建时段）。
- 患者登录采用演示账号还是接入微信授权（默认：先实现可演示登录，微信授权作为集成阶段）。
- 一个账号可维护多位就诊人，预约时选择实际就诊人；账号资料与真实就诊信息分别管理。
- Redis 是否进入 Docker Compose（默认：进入开发环境 Compose，生产单独部署）。
