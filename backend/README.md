# 后端工程

本工程使用 Java 17、Spring Boot 3.5.16、MyBatis 3.0.5、MySQL 8.0.45，以及 Redis 3.2.100 的 RESP2 协议。Maven 编译强制 Java 17。

从项目根目录运行 `scripts/Start-Local.ps1` 可加载被 Git 忽略的本地配置并启动工程。密码、JWT 签名密钥和演示账号密码均由环境变量提供。后端不会读取或使用微信密钥；本地患者通过设备标识演示登录。

## 模块

- `web`：JSON 请求 DTO 和 HTTP 控制器，只调用服务。
- `service`：认证、基础资料、排班、预约、用户、患者资料与操作日志；事务放在服务公共方法。
- `persistence`：MyBatis Mapper 和仅接受白名单标识符的 SQL Provider；所有数据通过参数绑定。
- `security`：JWT 签名与认证过滤器；每次请求再次检查数据库账号、角色和令牌撤销状态。
- `common`：`R<T>`、分页、统一异常、UTC/上海时区转换、预约状态机。
- `config`：安全、明确本地来源的跨域、Redis RESP2、本地演示数据。
- `db/migration`：Flyway 迁移，11 张业务表以及令牌、预约幂等两张支撑表。全部包含逻辑删除、创建时间、修改时间。

## 预约一致性

创建预约先锁患者账号，随后锁幂等请求（若提供幂等键）、排班；预约状态变更统一按排班→预约顺序加锁。排班行锁、条件号源更新、活动预约唯一索引共同防止重复与超额。有效状态仅为 `PENDING`、`CONFIRMED`，进入任何终态均释放一次号源。

关闭、停诊或删除排班会在一个事务内取消活动预约。停用科室、医生及账号同样会处理关联活动预约。预约历史保存医生、科室、患者和时段快照，业务删除不会物理移除行。

Redis 仅缓存诊所公开信息，缓存失效自动回查 MySQL。预约一致性和登录退出不会依赖 Redis。`local` 模式使用虚构资料和未来七天排班；退出 `local` 模式后演示登录及数据初始化不启用。

## 构建和验证

使用 JDK 17 执行 `mvn test` 或 `mvn package`。本地环境可使用项目根目录的启动与验证脚本。单元测试检查状态机终态、包装对象的级联校验、诊所日期的 UTC 范围以及 SQL 白名单。跨接口与并发验证由根目录 `tests/api.integration.test.mjs` 执行。

接口契约见 `../docs/API-CONTRACT.md`，运行后的 OpenAPI 地址为 `http://localhost:8080/v3/api-docs`，Swagger UI 为 `http://localhost:8080/swagger-ui/index.html`。运维健康地址为 `http://localhost:8080/actuator/health`。

## 官方参考

- [Spring Boot 官方仓库](https://github.com/spring-projects/spring-boot)
- [MyBatis Spring Boot Starter 官方仓库与版本兼容说明](https://github.com/mybatis/spring-boot-starter)
- [阿里巴巴 Java 开发手册公开仓库](https://github.com/alibaba/Alibaba-Java-Coding-Guidelines)
- [MySQL InnoDB 锁与事务文档](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html)

代码为本项目独立实现，未复制第三方预约系统源码。
