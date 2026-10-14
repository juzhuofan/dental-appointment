# 每周医生排班与 XXL-JOB

本项目使用独立的 XXL-JOB `3.5.0` 调度中心，业务后端作为执行器。调度目标是每周日北京时间 00:00 生成下周一至周日的排班，并排除已批准请假。执行器默认关闭。本次已在独立临时 MySQL 验证业务迁移与预置任务初始化 SQL；本机没有 Docker，尚未启动调度中心容器、注册在线执行器或启用每周真实调度。

## 版本与结构

- 后端只依赖 `com.xuxueli:xxl-job-core:3.5.0`，保持项目 Java 17 / Spring Boot 3.5.16。官方 core 的 Spring/Jakarta 依赖为 provided，不把调度中心的 Spring Boot 4 带进业务应用。
- 调度中心固定 `xuxueli/xxl-job-admin:3.5.0`，与执行器版本一致。3.5.0 改为每个执行器独立 AccessToken，不能沿用旧版全局 token。
- 调度中心使用独立 MySQL 容器和 `xxl_job` 库，业务 Flyway 不管理其表。`deploy/xxl-job/init/00-schema.sql` 取自同版官方 schema，去除默认账号和全部示例数据，仅保留调度锁；许可证和来源见同目录 NOTICE、LICENSE。
- `XxlJobConfig` 只在 `xxl.job.executor.enabled=true` 时创建执行器，启动前验证地址、AppName、令牌及端口；GLUE 功能固定关闭，只使用 Java BEAN 任务。

## 独立启动调度中心

先安装并启动 Docker Desktop 或可用的 Docker Engine + Compose。以下命令在项目的 `deploy/xxl-job` 目录执行：

```powershell
Copy-Item .env.example .env
# 编辑 .env，填写全部 REQUIRED 项，再执行：
docker compose --env-file .env -f compose.yml up -d
docker compose --env-file .env -f compose.yml ps
```

`.env` 必须自行填写 MySQL root 密码、XXL-JOB 数据库密码、调度中心管理员用户名/密码和执行器令牌；示例不包含可直接使用的密码或令牌。管理员用户名为 4 至 20 位英文字母、数字、点、下划线或短横线；管理员密码为 12 至 20 字符，与官方登录页面的上限一致；执行器令牌使用 32 至 255 位随机字母、数字、下划线或短横线。数据库密码建议至少 12 字符，不同密码应分别生成，不与业务系统共用。

首次启动初始化管理员、本项目执行器组及“每周生成医生排班”任务，任务字段已预配置，`trigger_status=0` 保持停止，不创建示例任务。访问 [本机 XXL-JOB 控制台](http://127.0.0.1:18080/)，使用自己填写的账号登录。MySQL 不映射宿主机端口；控制台默认只绑定本机 `127.0.0.1:18080`。需要远程部署时，应按实际网络调整访问地址和入口认证。

数据库初始化只在新数据卷首次启动时执行。修改 `.env` 后不会重置已有账号、密码或执行器 token；已有系统请通过控制台和数据库运维流程更新，不能用重新初始化替代迁移。已使用旧初始化脚本的数据卷不会自动补建任务，请在控制台按下表手工创建并保持停止；已存在同 Handler 任务时直接核对配置，不重复添加。不要把 `.env` 或含凭据的 `docker compose config` 输出提交到仓库。

## 启用业务后端执行器

在业务后端的 IDEA 运行配置或进程环境中设置下列变量。`deploy/xxl-job/.env` 由 Compose 读取，不会自动传给独立启动的业务后端：

| 环境变量 | 用途/示例 |
| --- | --- |
| `XXL_JOB_EXECUTOR_ENABLED` | 验收配置后显式设为 `true`，默认 `false` |
| `XXL_JOB_ADMIN_ADDRESSES` | `http://127.0.0.1:18080`，多个调度中心根地址以逗号分隔 |
| `XXL_JOB_EXECUTOR_APPNAME` | `dental-appointment-executor`，与初始化执行器组一致 |
| `XXL_JOB_EXECUTOR_ACCESS_TOKEN` | 与该执行器组中的 token 一致，不能填公开默认值 |
| `XXL_JOB_EXECUTOR_PORT` | 默认 `9999`，是执行器通讯端口，不是后端 API 端口 |
| `XXL_JOB_EXECUTOR_ADDRESS` | 本机后端 + Docker 调度中心可用 `http://host.docker.internal:9999/` |
| `XXL_JOB_EXECUTOR_IP` | 可选；未填写 address 时用于自动组成注册地址 |
| `XXL_JOB_EXECUTOR_LOG_PATH` | 默认 `.local/xxl-job/jobhandler`，相对于后端进程工作目录 |
| `XXL_JOB_EXECUTOR_LOG_RETENTION_DAYS` | 默认 30，允许 3 至 3650 |
| `XXL_JOB_ADMIN_TIMEOUT` | 调度中心通讯超时，默认 3 秒，允许 1 至 10 |

3.4.1 起 `admin.addresses` 使用根地址，不能追加旧版 `/xxl-job-admin`。Windows 本机后端被 Docker 中的调度中心调用时，不能把执行器注册地址填成 `127.0.0.1`；调度中心必须能连接注册地址和通讯端口。Compose 已提供 `host.docker.internal` 的宿主机映射，其他部署方式请填写实际可达地址。

启用后重启业务后端，在控制台“执行器管理”确认本项目 AppName 下出现在线地址。该步骤只注册执行器；新数据卷预置的任务仍保持停止。

## 核对预置任务，首次保持停止

新数据卷启动后，在控制台“任务管理”找到“每周生成医生排班”，核对下表配置，完成“执行一次”验收后再启动，无需重复填写或新增任务。旧数据卷若没有该任务，手工新增并按下表填写，保存后先保持停止：

| 配置项 | 值 |
| --- | --- |
| 执行器 | `dental-appointment-executor` |
| 任务名称 | 每周生成医生排班 |
| 调度类型 | CRON |
| Cron | `0 0 0 ? * SUN` |
| 运行模式 | BEAN |
| JobHandler | `weeklyDoctorScheduleJob` |
| 执行参数 | 日常调度留空 |
| 路由策略 | 第一个 / `FIRST` |
| 阻塞处理策略 | 单机串行 / `SERIAL_EXECUTION` |
| 调度过期策略 | 立即执行一次 / `FIRE_ONCE_NOW` |
| 失败重试次数 | `3` |
| 任务超时时间 | 建议 `300` 秒，按实际医生数量验收后调整 |
| 告警 | 填写可用收件人，并另外配置调度中心邮件服务；未配置邮件不代表已收到告警 |

调度中心 JVM 明确使用 `-Duser.timezone=Asia/Shanghai`，容器同时设置 `TZ=Asia/Shanghai`；Cron 按调度中心 JVM 默认时区解释，单独调整 Jackson 或 JDBC 的时区不能替代它。业务生成逻辑始终按 `Asia/Shanghai` 计算自然周，再转换为 UTC 入库。

路由 `FIRST` 只选择一个执行器；单机串行限制同任务在该执行器上的运行顺序，不能代替数据库幂等。不要把此任务改成分片广播或“覆盖之前调度”。先通过“执行一次”验收，再点击“启动”启用每周调度。失败重试和补偿都可能重复调用，业务必须保持幂等。

## 排班和请假规则

1. 日常参数为空时，以 XXL-JOB 本次日志触发时间确定最近一个周日调度周期，生成该周日之后的周一至周日；周一延迟执行仍补本周，不会误排到再下一周。调度迟到时只补尚未过预约截止时间的时段。
2. 手动补跑参数填一个纯日期，例如 `2026-10-19`，必须是本周至未来八周内的周一。不要填写 `targetMonday=...` 或 JSON，不接受过去自然周。补跑本周时已过期/截止时段跳过。
3. 每名启用医生优先沿用目标前一周的有效名义排班；若该周没有来源，回查最近有有效来源的周，沿用其星期、时段、容量及取消截止分钟数。请假跳过的名义排班由生成台账保留，整周或部分请假结束后可以恢复正常周模式。
   正常来源为已发布或关闭排班，以及有请假停诊来源标记的原正式排班；草稿、普通手工停诊和已删除记录不直接作为来源。生成后统一发布，可预约数从完整容量开始，不复制原预约或已占号。
4. 只有已批准请假排除相交时段；待审批、拒绝或撤销请假不作为排班排除依据。批准请假时的已有预约处理按请假审批流程执行，生成任务不会把已取消预约恢复回来。
5. 已有排班、已生成记录不重复插入，不改写人工排班及已占号。手工创建、自动生成、请假批准共用医生相关锁和时间冲突校验；数据库台账与唯一约束承担重试幂等。
6. 没有任何来源周的医生需要先由管理员维护首周。报告列出无来源医生、回退来源医生和失败医生；无来源或失败会将本次任务标为失败，以供重试和告警。其他医生已完成的结果保留，重试不会重复生成。
7. 自动任务采用系统标识：`schedule.created_by=0`，审计操作者为空、角色 `SYSTEM`；不会伪造管理员登录，也不会为执行器注入患者/管理员请求身份。

## 验收范围

先在独立测试环境准备启用医生、来源周排班和已批准/待审批请假。保持自动任务停止，依次手动验证正常生成、请假跳过、同参数重试、人工排班冲突、整周请假后的恢复、无来源医生失败、周一补偿和非法日期。核对生成时段、容量、台账、审计记录及任务报告，再启用正式周日 Cron。

配置单元测试通过构造替身验证绑定和受支持的 API，不启动执行器。独立 MySQL 8.0.45 的 10 项集成测试已通过，包含业务 V1–V7 迁移、并发生成/审批、重复执行、请假后恢复，以及原始调度中心 DDL 和预置任务 SQL 的实际执行。Shell 语法检查也已通过；尚未验证 Docker 容器网络、调度中心界面、执行器远程触发或每周真实触发，部署后仍需完成上述连通和调度验收。

本机复测使用 `scripts/Test-WeeklyScheduleDatabase.ps1`，只启动绑定 `127.0.0.1:13316` 的新临时实例，拒绝复用已有端口，不读取业务数据库配置。脚本结束时只关闭本次实例，数据目录和日志保留在 `.local/weekly-mysql-*`。完整验证记录见 [每周排班验证记录](WEEKLY-SCHEDULE-VERIFICATION-2026-10-15.md)。

## 官方依据

- [3.5.0 正式发布和 AccessToken 协议变化](https://github.com/xuxueli/xxl-job/releases/tag/3.5.0)
- [同版 core POM](https://github.com/xuxueli/xxl-job/blob/3.5.0/xxl-job-core/pom.xml)
- [官方执行器配置类](https://github.com/xuxueli/xxl-job/blob/3.5.0/xxl-job-executor-samples/xxl-job-executor-sample-springboot/src/main/java/com/xxl/job/executor/config/XxlJobConfig.java)
- [官方配置与运行策略文档](https://github.com/xuxueli/xxl-job/blob/3.5.0/doc/XXL-JOB%E5%AE%98%E6%96%B9%E6%96%87%E6%A1%A3.md)
- [Cron 默认时区实现](https://github.com/xuxueli/xxl-job/blob/3.5.0/xxl-job-admin/src/main/java/com/xxl/job/admin/business/scheduler/cron/CronExpression.java)
- [官方登录页及账号长度限制](https://github.com/xuxueli/xxl-job/blob/3.5.0/xxl-job-admin/src/main/resources/templates/framework/base/login.ftl)
- [官方登录密码校验](https://github.com/xuxueli/xxl-job/blob/3.5.0/xxl-job-admin/src/main/java/com/xxl/job/admin/framework/controller/LoginController.java)
