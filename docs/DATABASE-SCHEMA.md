# 初版实际数据库结构

数据库：`dental_appointment`；MySQL 8.0.45；字符集 `utf8mb4`。以下字段取自已成功迁移的本机数据库，完整索引、外键和约束以 `backend/src/main/resources/db/migration/V1__create_core_tables.sql` 为准。

V1 的 13 张业务及支撑表以及 V5 新增的 2 张 AI 表均使用 `deleted` 逻辑删除，并记录 `created_at`、`updated_at`。时间以 UTC 存储，页面按上海时区显示；Flyway 自身版本记录表由框架维护。AI 新表结构见本文末尾，完整列与索引以各版本迁移脚本为准。

## appointment（预约记录）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| appointment_no | varchar(32) | 否 | NULL |  |
| patient_user_id | bigint unsigned | 否 | NULL |  |
| patient_profile_id | bigint unsigned | 否 | NULL |  |
| schedule_id | bigint unsigned | 否 | NULL |  |
| doctor_id | bigint unsigned | 否 | NULL |  |
| department_id | bigint unsigned | 否 | NULL |  |
| patient_name_snapshot | varchar(80) | 否 | NULL |  |
| patient_phone_snapshot | varchar(20) | 否 | NULL |  |
| doctor_name_snapshot | varchar(80) | 否 | NULL |  |
| department_name_snapshot | varchar(80) | 否 | NULL |  |
| start_time_snapshot | datetime(3) | 否 | NULL |  |
| end_time_snapshot | datetime(3) | 否 | NULL |  |
| chief_complaint | varchar(500) | 是 | NULL |  |
| status | varchar(16) | 否 | NULL |  |
| appointment_active_key | varchar(80) | 是 | NULL |  |
| cancel_reason | varchar(255) | 是 | NULL |  |
| cancelled_at | datetime(3) | 是 | NULL |  |
| handled_by | bigint unsigned | 是 | NULL |  |
| handled_at | datetime(3) | 是 | NULL |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |

## appointment_idempotency（预约幂等请求）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| user_id | bigint unsigned | 否 | NULL |  |
| request_key | varchar(100) | 否 | NULL |  |
| request_hash | char(64) | 否 | NULL |  |
| appointment_id | bigint unsigned | 是 | NULL |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |

## auth_token（令牌撤销记录）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| token_id | varchar(64) | 否 | NULL |  |
| user_id | bigint unsigned | 否 | NULL |  |
| expires_at | datetime(3) | 否 | NULL |  |
| revoked | tinyint | 否 | 0 |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |

## clinic_notice（诊所公告）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| title | varchar(120) | 否 | NULL |  |
| content | text | 否 | NULL |  |
| status | tinyint | 否 | 0 |  |
| publish_at | datetime(3) | 是 | NULL |  |
| expire_at | datetime(3) | 是 | NULL |  |
| created_by | bigint unsigned | 否 | NULL |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |

## department（科室）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| name | varchar(80) | 否 | NULL |  |
| description | varchar(1000) | 是 | NULL |  |
| avatar_url | varchar(500) | 是 | NULL | V6 新增；仅保存 OSS 普通 URL |
| sort_order | int | 否 | 0 |  |
| status | tinyint | 否 | 1 |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |
| active_name | varchar(80) | 是 | NULL | STORED GENERATED |

## doctor（医生档案）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| user_id | bigint unsigned | 是 | NULL |  |
| department_id | bigint unsigned | 否 | NULL |  |
| name | varchar(80) | 否 | NULL |  |
| title | varchar(80) | 是 | NULL |  |
| specialty | varchar(500) | 是 | NULL |  |
| introduction | text | 是 | NULL |  |
| avatar_url | varchar(500) | 是 | NULL |  |
| status | tinyint | 否 | 1 |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |
| active_user_id | bigint unsigned | 是 | NULL | STORED GENERATED |

## doctor_schedule（医生排班）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| doctor_id | bigint unsigned | 否 | NULL |  |
| department_id | bigint unsigned | 否 | NULL |  |
| start_time | datetime(3) | 否 | NULL |  |
| end_time | datetime(3) | 否 | NULL |  |
| total_slots | int unsigned | 否 | NULL |  |
| booked_slots | int unsigned | 否 | 0 |  |
| status | varchar(16) | 否 | NULL |  |
| cancel_before_minutes | smallint unsigned | 否 | 120 |  |
| version | int unsigned | 否 | 0 |  |
| created_by | bigint unsigned | 否 | NULL |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |
| active_period | varchar(100) | 是 | NULL | STORED GENERATED |

## operation_log（操作日志）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| operator_user_id | bigint unsigned | 是 | NULL |  |
| operator_name | varchar(80) | 是 | NULL |  |
| operator_role | varchar(32) | 是 | NULL |  |
| action | varchar(64) | 否 | NULL |  |
| target_type | varchar(64) | 否 | NULL |  |
| target_id | varchar(64) | 是 | NULL |  |
| summary | varchar(500) | 是 | NULL |  |
| ip_address | varchar(45) | 是 | NULL |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |

## patient_profile（患者就诊资料）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| user_id | bigint unsigned | 否 | NULL |  |
| real_name | varchar(80) | 否 | NULL |  |
| phone | varchar(20) | 否 | NULL |  |
| gender | tinyint | 否 | 0 |  |
| birth_date | date | 是 | NULL |  |
| remark | varchar(255) | 是 | NULL |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |

## sys_role（角色）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| role_code | varchar(32) | 否 | NULL |  |
| role_name | varchar(64) | 否 | NULL |  |
| description | varchar(255) | 是 | NULL |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |

## sys_user（账号）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| username | varchar(64) | 否 | NULL |  |
| password_hash | varchar(100) | 否 | NULL |  |
| phone | varchar(20) | 是 | NULL |  |
| wechat_openid | varchar(128) | 是 | NULL |  |
| demo_device_hash | char(64) | 是 | NULL |  |
| display_name | varchar(80) | 否 | NULL |  |
| avatar_url | varchar(500) | 是 | NULL |  |
| status | tinyint | 否 | 1 |  |
| last_login_at | datetime(3) | 是 | NULL |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |
| active_username | varchar(64) | 是 | NULL | STORED GENERATED |
| active_phone | varchar(20) | 是 | NULL | STORED GENERATED |
| active_openid | varchar(128) | 是 | NULL | STORED GENERATED |
| active_device_hash | char(64) | 是 | NULL | STORED GENERATED |

## sys_user_role（账号角色关系）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| user_id | bigint unsigned | 否 | NULL |  |
| role_id | bigint unsigned | 否 | NULL |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |

## system_config（诊所配置）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | NULL | auto_increment |
| config_key | varchar(100) | 否 | NULL |  |
| config_value | varchar(2000) | 否 | NULL |  |
| description | varchar(255) | 是 | NULL |  |
| updated_by | bigint unsigned | 是 | NULL |  |
| deleted | tinyint | 否 | 0 |  |
| created_at | datetime(3) | 否 | NULL |  |
| updated_at | datetime(3) | 否 | NULL |  |

## V5：ai_conversation（AI 会话）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | 自动生成 | 自增主键 |
| user_id | bigint unsigned | 否 | 无 | 当前认证账号 |
| title | varchar(80) | 否 | 新会话 | 第一条消息摘要 |
| version | int | 否 | 0 | 并发版本号 |
| deleted | tinyint | 否 | 0 | 逻辑删除 |
| created_at | datetime(3) | 否 | 无 | UTC 新增时间 |
| updated_at | datetime(3) | 否 | 无 | UTC 修改时间 |

索引：`idx_ai_conversation_user(user_id, deleted, updated_at, id)`。

## V5：ai_message（AI 消息）

| 字段名 | 类型 | 可为空 | 默认值 | 补充 |
| --- | --- | --- | --- | --- |
| id | bigint unsigned | 否 | 自动生成 | 自增主键、历史游标 |
| conversation_id | bigint unsigned | 否 | 无 | 所属会话 |
| request_id | varchar(64), ascii_bin | 否 | 无 | 区分大小写的发送标识 |
| role | varchar(16) | 否 | 无 | USER / ASSISTANT |
| content | text | 否 | 无 | 用户可见消息 |
| metadata_json | json | 是 | NULL | 医生号源与出处快照 |
| deleted | tinyint | 否 | 0 | 逻辑删除 |
| created_at | datetime(3) | 否 | 无 | UTC 新增时间 |
| updated_at | datetime(3) | 否 | 无 | UTC 修改时间 |

唯一索引：`uk_ai_message_request(conversation_id, request_id, role)`。历史索引：`idx_ai_message_conversation(conversation_id, deleted, id)`。V5 已在独立 `dental_appointment_ai_test` 库验证，业务库通过后端启动时的 Flyway 自动迁移。会话删除同一事务逻辑删除两表记录。完整流程见 [AI-CHAT.md](AI-CHAT.md)。
