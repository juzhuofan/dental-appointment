# 后端工程

Java 17、Spring Boot 3.5.16、MyBatis-Plus 3.5.17、MySQL 8.0.45 和 Redis 3.2.100。项目使用 Maven 3.10.0 构建，数据库由 Flyway 管理，接口前缀为 `/api/v1`。

## 目录与职责

```text
src/main/java/com/dental/
├─ DentalAppointmentApplication.java
├─ common/        R<T>、分页、统一异常、公共实体字段与时间转换
├─ config/        Spring Security、Redis、Jackson、CORS、OpenAPI、MyBatis-Plus、OSS
├─ security/      Bearer token 过滤器、当前用户与权限支持
├─ auth/          登录、演示登录、退出及令牌失效
├─ user/          账号、角色及患者资料
├─ department/    科室与诊所设置
├─ doctor/        医生档案
├─ schedule/      排班与号源
├─ appointment/   预约、取消、状态处理与统计
├─ notice/        公告
├─ file/          通用阿里云 OSS 文件上传、上传结果链接
└─ audit/         操作日志
```

数据库业务目录下都有 `controller/`、`service/`、`mapper/`、`entity/`、`dto/`、`vo/`。普通持久化操作由 service 调用 MyBatis-Plus；复杂的行锁、分页联查及预约统计位于 `src/main/resources/mapper/*.xml`。建表与初始化 SQL 位于 `src/main/resources/db/migration/`。所有业务表使用 `deleted` 逻辑删除标志和 `created_at`、`updated_at` 字段；已执行的 Flyway 迁移不得直接修改。

`file` 为无数据库持久化的通用上传模块，包含 `controller/`、`service/`、`vo/`，无需建立 Mapper、数据库实体或迁移脚本。业务层注入 `FileUploadService` 后调用 `upload(file).url()`，再由原有业务服务把链接保存到对应字段。

## OSS 文件上传

`POST /api/v1/files/upload` 接收 `multipart/form-data` 的单文件字段 `file`，使用现有 Bearer 登录认证，响应为 `R<FileUploadVO>`。文件二进制不使用 JSON 的 `R.data` 请求包装。默认单文件 10 MiB，请求 11 MiB，支持图片、PDF 和常用文档扩展名。

`application.yml` 的 `app.oss` 保存非敏感静态配置；真实 AccessKey ID/Secret 保存在 Git 忽略的 `application-local.yml`。首次使用先复制 `application-local.example.yml` 并填写。更换部署环境时填写该环境的凭据、Bucket、Endpoint 和 region，再重启。关闭上传时接口返回 503。返回链接不加密，不带临时签名；模块不修改云端读权限，私有对象的链接无法匿名打开。

完整步骤、最小 RAM 权限策略、前端调用和 Java 复用方式见 [OSS 文件上传说明](../docs/OSS-UPLOAD.md)。SDK 上传客户端采用单例，关闭 Spring 容器时释放连接池。2026-10-07 Maven verify 的 28 项测试通过，真实 SDK 本地 HTTP 上传和真实 OSS 上传均验证成功；当前测试对象匿名访问返回 403。

## 本机 Maven 与 IDEA

本机 Maven Home：`D:\itApps\maven\apache-maven-3.10.0`。在 IDEA 中打开本目录的 `pom.xml`，项目 SDK、Maven Importer 和 Maven Runner 均选择 `C:\jdk\jdk17`，Maven 用户设置文件选择 `D:\itApps\maven\apache-maven-3.10.0\conf\settings.xml`。该设置文件把本地仓库设在 `D:\itApps\.m2\repository`，缺少的依赖从阿里云 Maven 镜像获取。修改设置后在 Maven 工具窗口执行“重新加载所有 Maven 项目”。

本机命令行构建：

```powershell
$env:JAVA_HOME = 'C:\jdk\jdk17'
& 'D:\itApps\maven\apache-maven-3.10.0\bin\mvn.cmd' `
  -s 'D:\itApps\maven\apache-maven-3.10.0\conf\settings.xml' verify
```

从项目根目录运行 `scripts/Build-Local.ps1` 可同时构建后端和两个前端。运行前在根目录准备 `.env.local`，填入本机 MySQL、Redis、JWT 密钥与演示账号密码。不要提交该文件。`local` 环境仅用于本地演示账号和虚构诊所数据初始化。

## 接口与验证

患者端已接入真实微信登录，非敏感设置位于 `application.yml` 的 `app.wechat`，AppSecret 位于本机 `application-local.yml`，演示点击登录默认关闭。启动只检查已有 token，未登录显示选项；用户点击微信登录后才验证身份，不强制头像。当前个人主体的手机号在就诊资料中填写，头像可在个人中心单独上传 OSS。微信 `text/plain` JSON 响应由字符串读取后解析，避免误报网络连接失败。头像展示使用临时签名地址，数据库保存普通 OSS URL。微信后台配置与真机验收见 [WECHAT-LOGIN.md](../docs/WECHAT-LOGIN.md)。

JSON 写请求使用 `{ "data": <DTO> }`，响应统一为 `R<T>`：`code`、`message`、`data`、`traceId`。查询参数在 URL 中；预约幂等键使用 `Idempotency-Key` 请求头。完整路径与字段以 [接口契约](../docs/API-CONTRACT.md) 为准。

后端单元测试：运行上面的 Maven `verify`。本地服务启动后，在项目根目录执行 `npm run test:api`；管理端和患者端预览同时启动时可执行 `npm run test:ui`。集成测试应使用独立测试数据库，避免影响人工演示数据。
