# 开发与生产环境配置

三个工程各使用三份位于工程标准位置的配置文件：

| 工程 | 环境选择入口 | 开发配置 | 生产配置 |
| --- | --- | --- | --- |
| Java 后端 | `backend/src/main/resources/application.yml` | `backend/src/main/resources/application-dev.yml` | `backend/src/main/resources/application-prod.yml` |
| Vue 管理端 | `admin-web/.env` | `admin-web/.env.development` | `admin-web/.env.production` |
| 微信小程序 | `miniapp/.env` | `miniapp/.env.development` | `miniapp/.env.production` |

三端可以分别切换。后端使用 Spring Boot 标准 Profile；Vue 和小程序使用 Vite 的 mode 命名。版本库中的后端配置只保留环境变量占位符；本机可以继续使用明文 YAML，真实凭据放在 Git 忽略的 `application-private-dev.yml`、`application-private-prod.yml` 中，由相应 Profile 自动导入。

## Java 后端

打开 `backend/src/main/resources/application.yml`，修改唯一入口：

```yaml
spring:
  profiles:
    active: dev
```

`dev` 加载 `application-dev.yml`，`prod` 加载 `application-prod.yml`。在 IDEA 启动 `com.dental.DentalAppointmentApplication` 或运行 JAR 时，Spring Boot 会自动读取这些资源文件。修改 YAML 后重启后端。若 IDEA 运行配置另行设置了 `SPRING_PROFILES_ACTIVE`、`spring.profiles.active` 或 `spring.config.location`，外部设置会优先，应取消覆盖后再用 `application.yml` 切换。

开发配置连接本机 MySQL/Redis，开发演示账号和目录初始化仅在 `dev` Profile 运行。生产配置假定 MySQL/Redis 与应用位于同一台服务器，后端仅监听 `127.0.0.1:8080`，演示登录与演示数据初始化关闭。首次克隆可将 `backend/config/application-private.yml.example` 复制为相应的 `backend/src/main/resources/application-private-<环境>.yml` 后填写凭据。私有文件仅保留凭据，公共配置中的端口、连接地址和业务开关仍可正常调整；禁止提交私有文件。

## Vue 管理端

打开 `admin-web/.env`，将 `APP_ENV` 设为 `development` 或 `production`。执行 `npm run dev`、`npm run build` 时，脚本读取对应的 `.env.development` 或 `.env.production`，并传递给 Vite。

开发模式将 `/api` 代理到本机 `127.0.0.1:8080`。生产模式使用同源 `/api/v1`；部署管理端的服务器需要把 `/api` 反向代理到后端。生产模式下在本机运行 Vite 开发服务器时，代理目标是已知服务器 `47.109.48.12`。

## 微信小程序

打开 `miniapp/.env`，将 `APP_ENV` 设为 `development` 或 `production`。运行 `npm run build:mp-weixin` 后，在微信开发者工具重新编译。H5 的 `dev:h5`、`build:h5` 也使用同一套文件。

开发 API 为 `http://127.0.0.1:8080/api/v1`。真机连本机后端时，把 `miniapp/.env.development` 改为手机可访问的局域网地址。生产文件目前使用服务器 `http://47.109.48.12/api/v1`；正式发布前需换为微信后台已配置的 HTTPS API 域名。当前资料里没有可用的生产 HTTPS 域名。

## 构建与启动

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Local.ps1
```

脚本读取 Java 的 `application.yml` 和两端的 `.env`，按各自选择构建。普通本机构建可能将未跟踪的私有 YAML 一并打包，因此 JAR 只可交付可信服务器，不能上传公共仓库。正式发布包应移除内嵌私有 YAML，并从服务器受保护的外部配置目录加载凭据。产物分别位于 `backend/target/`、`admin-web/dist/`、`miniapp/dist/build/mp-weixin/`。

本机启动脚本 `scripts/Start-Local.ps1` 要求后端选择 `dev`，管理端和小程序预览各自读取根目录 `.env`。项目根目录 `.env.local` 只用于 Windows 脚本的 Java/Maven 路径与端口；演示密码写在 `application-private-dev.yml`。直接在 IDEA 启动后端时，仍以 `application.yml` 为入口。
