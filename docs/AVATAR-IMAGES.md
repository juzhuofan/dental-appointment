# 科室与员工头像

科室使用 Flyway V6 新增的 `department.avatar_url`。`doctor.avatar_url`、`sys_user.avatar_url` 已存在。三个字段均只保存 OSS 返回的普通 HTTPS URL，不保存图片二进制、Base64、对象键或签名参数。

管理端在科室、医生档案、账号编辑表单中上传 JPG、PNG 或 WebP 图片。文件先经 `POST /api/v1/files/upload` 写入 OSS，保存表单时才把返回的 URL 写入对应业务行；取消表单不会关联图片，旧对象也不会自动删除。账号管理只向 ADMIN、DOCTOR 角色显示上传控件，患者微信头像流程未改。

科室、医生及后台账号的 API 响应同时给出 `avatarUrl` 和 `avatarDisplayUrl`。前者为数据库值，后者为服务端按当前 OSS Bucket 生成的 15 分钟读取地址；前端优先用后者显示。科室首页、医生列表与详情、管理端列表和右上角账号头像均已接入，缺少图片时保留原有占位样式。

本机 2026-10-14 的虚构演示数据已填充 4 个科室、6 位医生和 2 个后台账号；其中 `doctor` 账号与“林知安”医生档案共用同一张原图。11 张原图及生成提示词保存在 Git 忽略的 `.local/generated-avatars/`，OSS 地址映射保存在 `.local/generated-avatar-urls.json`。若迁移到其他 Bucket，应重新上传并通过管理端保存新的 URL。

实际验证：Flyway V6 成功；后端公开科室、公开医生、医生详情、管理账号列表和 `/me` 均返回正确的稳定 URL 与临时展示地址；12 个展示地址逐个 GET，均为 HTTP 200、`image/png`，文件大小与原图一致。管理端 Playwright 测试确认管理员、科室、医生头像和编辑预览真实加载，头像选择会发送 multipart 上传请求并更新预览；上传请求在该浏览器测试中使用模拟响应，实际 OSS 写入另经上传接口验证。科室、医生和后台账号各做过一次头像移除与恢复的 API 写入验证，最终均恢复原 URL；后台管理员的原登录令牌在修改头像后仍可访问 `/me`。

2026-10-14 小程序模拟器仍显示占位图的原因：当时的 `miniapp/.env.local` 指向未更新头像接口的远端后端，远端 `/departments` 和 `/doctors` 均不返回头像字段。该旧配置文件现已被根目录 `.env` 与 `.env.development`、`.env.production` 替代。开发构建使用 `http://127.0.0.1:8080/api/v1` 并重新生成 `dist/build/mp-weixin/`；同一套 Vue 页面组件的 H5 预览曾实测加载 4 张科室图片和首页 3 张医生头像。微信开发者工具需要对更新后的构建目录重新编译。
