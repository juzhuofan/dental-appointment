# 口腔诊所门诊预约系统

本科毕业设计初版：Spring Boot + Vue 3 管理后台 + uni-app 微信小程序 + MySQL + Redis。

## 工程目录

```text
backend/       Java 17 / Spring Boot / MyBatis / Spring Security 服务端
admin-web/     Vue 3 / TypeScript / Element Plus 管理后台
miniapp/       uni-app Vue 3 微信小程序及辅助 H5 预览
scripts/       Windows 本地构建、启动、停止和验证脚本
tests/         真实本地服务接口验证及管理端、患者端浏览器验证
docs/          设计文档、接口契约、实现约定与验证记录
.env.example   可提交的配置模板
.env.local     仅保存在本机的连接密码和演示账号配置（Git 忽略）
.local/        本地日志、进程记录、缓存、验证截图（Git 忽略）
```

## 开发环境

必须使用 Java 17、Node.js 24.18.0、MySQL 8.0.45、Redis 3.2.100。Redis 为本机 Windows 移植版，本项目只使用基础缓存命令。管理端与小程序依赖分别固定在各自的 package-lock.json 中；详细 Java 依赖见 backend/pom.xml。

本机配置已放入 `.env.local`，其中 JAVA_HOME 为 `C:\jdk\jdk17`。后端和所有脚本均读取该文件；不要将其加入 Git。数据库名称为 `dental_appointment`，数据库表通过 Flyway 自动创建。后端初次启动会创建虚构演示科室、医生、未来排班以及管理员/医生账号。

如在另一台电脑运行，先复制 `.env.example` 为 `.env.local`，填写连接密码、随机 JWT_SECRET、DEMO_ADMIN_PASSWORD 和 DEMO_DOCTOR_PASSWORD，再创建空数据库：

```sql
CREATE DATABASE IF NOT EXISTS dental_appointment
CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

## 构建与启动

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Local.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\Start-Local.ps1
```

首次构建需要下载 Maven/npm 依赖，后续构建可以加 `-SkipInstall` 跳过 npm 重装。更新已运行工程前，先执行停止脚本，避免 Windows 锁定后端 JAR。Maven 优先使用 `.env.local` 的 MAVEN_HOME 或 PATH 中的 Maven；当前机器通过 MAVEN_HOME 使用本机 Maven 3.10.0。Maven 会先从 `settings.xml` 配置的本地仓库读取依赖，再从阿里云镜像下载；当前机器配置路径为 `D:\itApps\.m2\repository`。其他电脑未安装 Maven 时可使用官方 Wrapper（固定 3.9.11，首次需下载）。npm 缓存写入项目本地目录。

启动后访问：

- 管理后台：http://127.0.0.1:5173/
- 患者端 H5 预览：http://127.0.0.1:5174/（用于辅助检查小程序页面）
- 后端 API：http://127.0.0.1:8080/api/v1
- 服务健康：http://127.0.0.1:8080/actuator/health
- 接口契约：[docs/API-CONTRACT.md](docs/API-CONTRACT.md)

管理员账号为 `admin`，密码见 `.env.local` 的 `DEMO_ADMIN_PASSWORD`；演示医生账号为 `doctor`，密码见 `DEMO_DOCTOR_PASSWORD`。本地演示初始化仅在 local 环境执行。修改密码后，后续启动保留新密码，不重新覆盖既有账号。

启动脚本默认同时开启患者 H5 预览；仅使用微信开发者工具时可以加 `-WithoutPatientPreview`。停止项目后端、管理端和患者预览：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Stop-Local.ps1
```

停止脚本仅停止本项目记录的进程，不停止 MySQL 或 Redis。日志见 `.local/backend.out.log`、`.local/backend.err.log`、`.local/admin.out.log`、`.local/patient.out.log`。

## 微信开发者工具

构建成功后，在微信开发者工具中导入 `miniapp` 目录；根目录的 project.config.json 已指定 `dist/build/mp-weixin/` 为小程序代码目录，AppID 已设置为用户提供的 ID。

微信开发者工具本地运行时关闭“校验合法域名”。默认 API 地址为 `http://127.0.0.1:8080/api/v1`，可以在 miniapp 环境配置中调整。真机联调需要将地址换成电脑局域网 IP，并保证手机能访问后端；当前交付重点是本机开发者工具演示。

患者点击“一键登录，开始预约”即可创建或恢复当前设备的演示患者，不需要真实微信授权。登录设备标识与令牌保存在小程序本地存储，后端只赋予患者权限。演示登录可通过 `APP_DEMO_LOGIN_ENABLED=false` 关闭；初版不需要微信 AppSecret。

## 建议演示流程

1. 管理员登录后台，查看或维护科室、医生和未来排班，发布可预约时段。
2. 患者在小程序点击直接登录，确认本人就诊姓名和手机号。
3. 选择医生和日期、查看号源，提交预约，在“我的预约”查看 PENDING 状态。
4. 管理员在预约管理中确认预约，医生账号可查看本人预约并完成接诊或登记爽约。
5. 患者在取消截止时间前取消预约，观察排班号源只恢复一次。
6. 管理员发布公告、查看统计和操作日志。

## 数据与接口规则

- 所有业务及身份/幂等支撑表包含 deleted、created_at、updated_at；删除均为逻辑删除，历史预约保留快照。Flyway 的迁移版本表由框架维护。
- JSON 请求体统一 `{ "data": ... }`，响应统一 `R<T>`：code、message、data、traceId。分页结果放在 data.records/total/page/size 中。
- 创建预约默认待管理员确认；事务锁和有效预约唯一键防止超额或重复预约；取消及终态只释放一次号源。
- 管理端角色为 ADMIN/DOCTOR，患者为 PATIENT；后端校验角色和数据归属，医生只能访问本人排班和接诊记录。
- 缓存失效不影响预约事务正确性；令牌撤销保存在数据库。所有密码/密钥通过本地配置注入。
- Java 遵循阿里巴巴 Java 开发手册黄山版；前端 TypeScript 严格模式和 Vue Composition API。

## 验证

服务启动后运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Verify-Local.ps1
```

接口验证会在独立标识下创建虚构验证数据，经应用逻辑删除接口清理，并保留审计记录。验证覆盖 R/参数校验、身份与角色、排班重叠、重复/并发预约、重复取消、停诊取消、公告逻辑删除及退出令牌失效。浏览器验证使用本机 Microsoft Edge，检查管理端各页面及科室维护、医生权限、患者完整预约取消流程、响应丢失后的幂等重试。结果与实际范围见 [docs/VERIFICATION.md](docs/VERIFICATION.md)。

## 设计与参考

- [原设计文档](docs/PROJECT-DESIGN.md)
- [初版接口契约](docs/API-CONTRACT.md)
- [实际数据库字段](docs/DATABASE-SCHEMA.md)
- [实现约定](docs/IMPLEMENTATION-NOTES.md)
- 本地 origin：`https://github.com/juzhuofan/dental-appointment.git`

本项目使用框架官方发行依赖，业务代码根据本课题独立实现。GitHub 公共参考与工具文档见原设计文档。GitHub 推送和后续部署根据用户后续指令执行。
