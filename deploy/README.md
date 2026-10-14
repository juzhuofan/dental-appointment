# 云服务器部署准备

每周自动排班使用独立 XXL-JOB 调度中心，部署配置在 [`xxl-job/`](xxl-job/)，版本、环境变量和启用步骤见 [XXL-JOB 部署说明](../docs/XXL-JOB.md)。业务后端的执行器默认关闭，须按说明配置并启用。

后端通过 `--spring.profiles.active=prod` 选择生产环境；仓库内 `application-prod.yml` 不含真实凭据。本机私有 YAML 不提交 Git，服务器使用受保护的外部 `config/application-prod.yml`。管理端和小程序以 production mode 构建。具体入口见 [环境配置说明](../docs/ENVIRONMENTS.md)。

服务器数据库、Redis、微信、OSS、DeepSeek 和 JWT 凭据只保存于服务器私有配置。部署前核对现有服务与配置并备份；数据库和 Redis 默认指向服务器本机 `127.0.0.1`，后端监听 `127.0.0.1:8080`，供同机 Nginx 反向代理。

## 构建

分别选择三端生产环境后，在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Local.ps1
```

本机后端包位于 `backend/target/dental-backend-0.1.0.jar`，管理端产物在 `admin-web/dist/`，小程序产物在 `miniapp/dist/build/mp-weixin/`。正式发布包必须移除内嵌 `application-private-*.yml`，并确认不含真实凭据；服务器沿用自己的外部配置。

服务器启动后端：

```bash
java -Xms128m -Xmx512m -jar /opt/dental/dental-backend-0.1.0.jar --spring.profiles.active=prod --spring.config.additional-location=optional:file:/opt/dental/config/
```

## 待完成事项

- 核对服务器 MySQL、Redis 及所有生产凭据，填写服务器外部 `config/application-prod.yml`，限制为服务账号可读。
- 配置 HTTPS 域名、Nginx 反向代理及微信小程序请求、上传和下载合法域名。
- 小程序生产 `.env.production` 目前使用服务器 HTTP IP；正式发布前改为最终 HTTPS API 域名并重新构建。
- 首次启动由 Flyway 在空数据库中建表，`dev` 演示数据初始化不用于生产。
