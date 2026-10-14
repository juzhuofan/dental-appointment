# XXL-JOB 部署来源

- 执行器与调度中心版本固定为 `3.5.0`，官方发布：https://github.com/xuxueli/xxl-job/releases/tag/3.5.0 。
- `init/00-schema.sql` 的表定义来自官方 tag `3.5.0` 的 `doc/db/tables_xxl_job.sql`，保留上游版权说明；去除了默认账号、示例执行器与示例任务，只保留必需的 `schedule_lock` 行。账号与本项目执行器由 `01-bootstrap.sh` 使用调用者提供的凭据初始化，同时预置本项目“每周生成医生排班”任务并保持停止，不创建示例任务。
- 上游 schema：https://github.com/xuxueli/xxl-job/blob/3.5.0/doc/db/tables_xxl_job.sql 。
- 上游使用 GPL-3.0，许可证原文见同目录 `LICENSE-XXL-JOB`。
- 调度中心独立使用自己的 `xxl_job` 数据库和数据卷，不在业务库执行此 schema，不由业务 Flyway 管理。
- 完整启动、任务配置与验收步骤见 `../../docs/XXL-JOB.md`。
