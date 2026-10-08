# 微信小程序真实登录与 OSS 头像

当前配置采用个人主体模式：`app.wechat.phone-number-enabled: false`。打开小程序时检查已有登录状态；未登录显示“微信一键登录”和暂未开放的“手机号登录”选项，点击微信登录后才取得真实微信凭证。头像不再是登录前置条件，可以登录后在个人中心选择并上传 OSS。系统不生成虚假微信身份、手机号或姓名。

普通微信登录按钮先让用户确认使用当前微信应用登录的账号。微信小程序的 `wx.login` 返回当前微信身份的一次性凭证，不提供切换多个微信账号的原生列表；手机号选择列表属于 `getPhoneNumber` 能力，当前个人主体未开通。微信头像与昵称也不会由 `wx.login` 自动返回。登录后“我的”展示账号昵称，并提供独立的账号资料编辑：`input type="nickname"` 供用户选用微信建议昵称或手动输入，`chooseAvatar` 供用户选用微信头像或其他图片。头像由后端上传 OSS，昵称存入 `sys_user.display_name`，均不影响就诊人真实姓名。

同一账号可管理多个就诊人。V4 Flyway 迁移移除 `patient_profile.user_id` 唯一约束，保留一位默认就诊人；预约界面可选择就诊人，防重键使用 `patient_profile_id:schedule_id`。已有就诊人和预约随迁移保留。

## 1. 首次进入和后续登录

1. 首次进入首页时检查已有业务 token。有效且属于微信账号时直接恢复用户资料。
2. 没有有效 token 时直接展示登录选项，不在启动阶段调用微信登录，也不自动开户。
3. 用户点击“微信一键登录”并确认使用当前微信账号后调用 `uni.login({ provider: 'weixin' })`，使用新的一次性 `loginCode` 和 `register: true` 发起后端认证；此时不要求选择头像。
4. 后端使用 AppID、AppSecret 和 `loginCode` 调用微信 `code2Session`。仅使用微信服务器返回的 OpenID 查找或创建 `PATIENT` 账号；不能信任客户端自报的 OpenID、用户 ID 或手机号。
5. 微信身份验证成功就保存业务 token；未设置账号昵称或头像的用户会进入“我的”并打开资料设置，也可关闭后稍后设置。个人模式不强制存在头像或手机号。
6. 在个人中心点击“更换头像”时，独立组件处理微信隐私授权、头像选择和 OSS 上传；头像上传失败不会撤销已经验证的登录身份。
7. 后续打开时，有效 token 的用户直接进入首页；失效则显示登录选项，用户点击后取得新的微信 code，由后端验证同一 OpenID 并签发 token。停用账号不会被自动恢复。

参考图片中的号码列表属于原生 `getPhoneNumber` 手机号授权，不是微信账号选择器。只有主体已开通手机号能力时，微信一键登录按钮才能触发该面板；启用后前端使用真实原生组件，不自行绘制号码或微信账号列表。当前个人模式使用当前微信账号验证身份，不能自动读手机号；需要切换微信账号时应在微信中操作。

`wx.checkSession` 检查微信 `session_key` 是否有效；项目接口使用自己的业务 token，由后端校验。两种登录状态的有效期不能互相代替。`loginCode` 只使用一次，不保存作后续身份凭据。

## 2. 后端静态配置

公共配置文件为 `backend/src/main/resources/application.yml`。MySQL、Redis、JWT、微信 AppSecret 和 OSS AccessKey 的真实值放在 Git 忽略的 `application-local.yml`，首次使用先复制 `application-local.example.yml`。以下字符串仅为示例，不能直接作为真实凭证使用：

```yaml
app:
  wechat:
    enabled: true
    phone-number-enabled: false
    app-id: "wx0000000000000000"
    app-secret: "请填入实际小程序 AppSecret"
  demo-login:
    enabled: false
  oss:
    enabled: true
    endpoint: "https://oss-cn-beijing.aliyuncs.com"
    region: "cn-beijing"
    bucket-name: "dental-backend"
    access-key-id: "请填入实际 AccessKey ID"
    access-key-secret: "请填入实际 AccessKey Secret"
    object-prefix: "dental/uploads"
```

真实的 `app-secret`、`access-key-id` 和 `access-key-secret` 仅填写在本机 `application-local.yml`；其中的演示登录也应为 `false`。重新启动后端才能应用配置修改。前端的 `miniapp/src/manifest.json` 和 `miniapp/project.config.json` 中的小程序 AppID 必须与后端相同；小程序代码不包含 AppSecret 或 OSS AccessKey。

后端启动时，Flyway 会应用 `V3__wechat_identity_case_sensitive.sql`，让 OpenID 唯一标识按大小写区分。不要通过删除迁移历史或清空用户表处理已有数据库。

## 3. 微信公众平台配置

使用同一个小程序账号登录微信公众平台，确认以下配置：

1. 在开发设置中取得小程序 AppID、AppSecret，AppSecret 只填写在后端。
2. 在用户隐私保护指引中声明实际收集的头像、账号标识及用途；以后启用微信手机号授权时补充手机号用途。指引应与实际页面和代码一致。
3. 项目通过微信原生 `agreePrivacyAuthorization` 按钮处理平台隐私授权。用户拒绝时保持未授权状态；普通勾选框不能代替微信平台授权。
4. 如微信后台启用了服务端 IP 白名单，将运行后端的公网出口 IP 按后台要求加入。开发电脑的局域网地址不是公网出口 IP。
5. 正式环境配置后端 HTTPS 域名为 `request` 和 `uploadFile` 合法域名；用到 `downloadFile` 时配置相应下载域名。直接从 OSS 读取头像时使用 `https://dental-backend.oss-cn-beijing.aliyuncs.com`，如改为自己的文件域名，相应调整配置和域名列表。

头像和手机号不是 `requiredPrivateInfos` 中的位置接口，不能把 `chooseAvatar` 或 `getPhoneNumber` 填入该数组。微信隐私 API 要求基础库至少 2.32.3，头像选择能力要求至少 2.21.2；本项目统一使用支持隐私 API 的基础库版本，并在真机验证。

个人主体模式保持 `phone-number-enabled: false`。以后切换认证非个人主体时，先确认微信后台已开通手机号快速验证权限及调用额度，再将该配置改为 `true`。用户点击 `getPhoneNumber` 按钮取得的 `phoneCode` 与 `uni.login` 的 `loginCode` 是不同凭证；手机号授权也不等于短信验证码登录。

## 4. OSS 存储与头像显示

头像接口仅允许当前有效微信患者账号更新自己的头像，客户端不提交目标用户 ID。支持 jpg、jpeg、png、gif、webp，限制为 2MB，同时检查文件内容与扩展名是否匹配。

数据库字段与返回值：

| 位置 | 含义 |
| --- | --- |
| `sys_user.wechat_openid` | 微信服务端验证的真实用户标识 |
| `sys_user.avatar_url` | OSS 稳定链接原文，不加密、不带临时签名 |
| `sys_user.phone` | 个人主体模式首次注册时为 `NULL`，不编造号码 |
| `sys_user.updated_at` | 更新头像时写入修改时间，`created_at` 保留 |
| `patient_profile.real_name`、`patient_profile.phone` | 首次微信注册时留空，实际预约前由用户完善 |
| `UserVO.avatarUrl` | 数据库保存的稳定链接 |
| `UserVO.avatarDisplayUrl` | 私有 Bucket 头像的临时读取链接，默认有效 15 分钟 |
| `UserVO.wechatBound` | 账号已关联微信 OpenID |
| `UserVO.profileCompleted` | 个人模式身份已关联微信即允许登录，不强制存在头像或手机号；手机号授权模式还需可信手机号 |

示例稳定地址：

```text
https://dental-backend.oss-cn-beijing.aliyuncs.com/dental/uploads/2026/10/07/<UUID>.png
```

当前 Bucket 可保持私有。后端通过 `FileUploadService.signedReadUrl` 为本 Bucket、配置目录下的对象生成短期读取链接，只在响应里提供，数据库继续保存无签名的原始地址。小程序展示 `avatarDisplayUrl`，下次恢复登录或查询 `/me` 时刷新地址，不把签名地址写回 `avatar_url`。

用于上传和签名的 OSS RAM 账号需要对 `dental-backend/dental/uploads/*` 具有所需 `oss:PutObject`、`oss:GetObject` 权限。无需开放整个 Bucket 的匿名读取权限；缺少读取权限时，即使生成了链接，访问仍可能返回 403。

## 5. 接口约定

JSON 请求和响应沿用 `R` 包装。上传是 multipart 二进制请求，不额外嵌套 JSON。

| 方法与路径 | 用途 | 认证 |
| --- | --- | --- |
| `GET /api/v1/auth/wechat-config` | 返回 `enabled`、`phoneNumberEnabled` 两个公开能力开关 | 无 |
| `POST /api/v1/auth/wechat-login` | 验证微信 code，查找或注册微信患者账号 | 无 |
| `POST /api/v1/me/avatar` | 上传并保存本人头像，文件字段 `file` | `Authorization: Bearer <token>` |
| `GET /api/v1/me` | 当前账号及头像显示链接 | 业务 token |
| `PUT /api/v1/me/patient-profile` | 完善真实就诊姓名、联系方式等资料 | 业务 token |

首次主动确认登录请求：

```json
{
  "data": {
    "loginCode": "微信运行环境返回的一次性 code",
    "register": true
  }
}
```

后端保留 `register: false` 查询已有微信身份的能力，但当前客户端启动不会自动调用它。个人模式不要求传 `phoneCode`；启用微信手机号能力后，新账号或尚未绑定手机号的账号需同时传入原生授权事件返回的 `phoneCode`。

登录成功时 `R.data` 包含 `token`、`user`。前端收到身份认证成功响应后保存登录状态，头像可为空。头像保存成功时 `R.data` 是更新后的 `UserVO`，属于独立资料更新操作。

## 6. 本地启动与微信开发者工具

1. 启动本机 MySQL、Redis，启动后端，确认 `http://127.0.0.1:8080/actuator/health` 可访问。
2. 在 `D:\GraduationProject\dental-appointment\miniapp` 运行 `npm run build:mp-weixin`。
3. 用微信开发者工具导入 `D:\GraduationProject\dental-appointment\miniapp`。根目录 `project.config.json` 的 `miniprogramRoot` 已指向实际编译目录 `dist/build/mp-weixin/`，不要将 `src` 当作可直接运行的原生小程序。
4. 选择支持 2.32.3 及以上功能的基础库，以有权限的真实微信账号登录开发者工具。开发阶段可按工具设置临时跳过合法域名校验；正式环境需要合法 HTTPS 域名和实际服务器配置。
5. 本机 `miniapp/.env.local` 已配置 `VITE_API_BASE_URL=http://10.212.182.144:8080/api/v1`。此地址是当前电脑 WLAN 地址，可用于同 Wi-Fi 真机调试；电脑地址变化后需重新填写并构建。真机中的 `127.0.0.1` 指手机自身，不能作为电脑后端地址。
6. 同一局域网调试时，可以在 `miniapp/.env.local` 配置 `VITE_API_BASE_URL=http://<电脑局域网IP>:8080/api/v1`，允许对应的本机防火墙访问后重新编译。在真机调试模式按微信工具要求处理域名校验；正式使用改为已配置的 HTTPS 域名。

用户操作及实际验证：

1. 使用未注册的真实微信账号进入，确认显示登录弹窗，没有演示账号自动成功。
2. 未点击微信登录时确认没有取得微信 code、没有自动开户；直接点击后验证能够在未选择头像的情况下完成身份登录。
3. 在个人中心单独更换头像，确认需要时处理真实隐私授权，头像上传 OSS、`sys_user.avatar_url` 保存无签名地址；取消或上传失败仍保持登录，数据库不编造姓名或手机号码。
4. 关闭并重新进入，确认已完成资料的同一微信账号直接进入首页，头像可显示。
5. 清除本地业务 token 后重新进入，确认显示登录选项；再次点击微信登录后恢复同一账号，没有重复开户。
6. 断网、OSS 上传失败、账号停用时确认显示失败状态，恢复正常条件后可以重试；不要用固定 code 或伪造 OpenID 模拟“真实登录成功”。

自动化测试使用 mock 微信接口、OSS 和数据库验证边界，不能替代用户在微信客户端执行以上真实授权流程。真实微信 code 必须由微信运行环境生成。

## 7. 常见报错

| 现象或错误码 | 原因与处理 |
| --- | --- |
| `WECHAT_REGISTRATION_REQUIRED` | 未注册用户的静默检查结果，显示登录弹窗，由用户主动确认注册 |
| `WECHAT_CODE_INVALID` | code 失效或重复使用，重新调用微信登录取得新 code |
| `WECHAT_LOGIN_DISABLED` | 后端微信登录关闭，检查静态配置后重启 |
| `WECHAT_PHONE_REQUIRED` | 已启用手机号能力但未提供授权凭证；个人小程序保持该能力关闭 |
| `WECHAT_CAPABILITY_UNAVAILABLE` 或 `getPhoneNumber:fail no permission` | 微信主体或接口权限不足，确认认证、能力开通、额度及配置 |
| `WECHAT_ACCOUNT_CONFLICT` | 已有 OpenID/手机号唯一约束或账号状态冲突，不自动合并其他用户 |
| `WECHAT_API_UNAVAILABLE` | 后端访问微信失败，检查后端网络和平台服务状况 |
| 头像上传 413 | 文件超过 2MB，选择更小的图片 |
| 头像上传 400 | 空文件、图片内容或扩展名不符合要求 |
| 头像上传 403 | 当前账号不是有效微信患者账号，或上传期间账号状态发生变化 |
| 头像显示 403 | 临时链接失效或 RAM 读取权限不足，刷新用户信息并检查 OSS 权限 |
| H5 页面提示使用微信小程序 | H5 可浏览公开诊所信息，真实微信登录须在微信小程序中操作 |

## 8. 本轮验证记录（2026-10-07）

以下是首版历史记录，登录流程已于 2026-10-08 修正为本说明第 1 节。当前回归：120 项后端测试、18 项客户端测试通过，微信 `text/plain` JSON 解析及官方接口错误分类已修复；小程序与 H5 已重新构建。电脑局域网健康接口返回 HTTP 200 / UP，用户在手机访问公开 `wechat-config` 接口已收到 OK。真机重启与重编译步骤见 [REAL-DEVICE-DEBUG.md](REAL-DEVICE-DEBUG.md)。

- JDK 17.0.14 / 本机 Maven 3.10.0：`verify` 的 109 项 JUnit 全部通过，125 个 Java 源文件编译及 JAR 打包成功。
- Node 24.18.0：11 项小程序登录状态与微信平台模拟测试通过；复验命令为项目根目录 `npm run test:miniapp-auth`。
- `npm run build:mp-weixin` 和 `npm run build:h5` 均成功。H5 不提供真实微信登录替代功能。
- 独立 `dental_appointment_codex_test` 库中 19 项贯通检查通过，包含 V3 大小写精确索引、首次静默不开户、主动注册、真实 JWT 鉴权、头像 HTTP 上传、实际 OSS 上传、数据库保存无签名链接、实际私有签名 GET 的图片内容校验，以及后续同一账号恢复。
- 贯通探针中的微信身份提供方使用虚构测试响应；数据库、JWT、Spring HTTP 和 OSS 使用真实组件。生成的 1×1 测试图片不含个人信息，测试账号及其资料、角色和令牌已逻辑删除，未使用业务数据库、未修改 OSS ACL。
- 已向微信官方接口验证现有 AppID/AppSecret，成功取得服务端访问令牌；令牌没有保存或显示。

独立贯通结果在 `.local/wechat-verification/result.json`，微信配置校验结果在同目录 `configuration-result.json`，均不含真实用户令牌、签名链接或服务端凭证。真实用户的 `wx.login` 和头像选择必须由用户在微信开发者工具或真机执行，第 6 节的人工授权验收尚未完成；自动化结果不代表已操作真实微信用户授权。

## 9. 官方参考

- [微信小程序登录流程](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/login.html)
- [微信 code2Session](https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html)
- [微信头像昵称填写能力](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/userProfile.html)
- [微信手机号快速验证组件](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/getPhoneNumber.html)
- [微信服务端获取手机号](https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-info/phone-number/getPhoneNumber.html)
- [微信隐私授权开发指南](https://developers.weixin.qq.com/miniprogram/dev/framework/user-privacy/PrivacyAuthorize.html)
- [微信官方 API 类型及对应接口说明](https://github.com/wechat-miniprogram/api-typings/blob/master/types/wx/lib.wx.api.d.ts)
- [微信官方头像组件与基础库要求](https://github.com/wechat-miniprogram/mp-user-avatar/blob/master/README.md)
- [腾讯官方非个人认证主体的手机号示例说明](https://cloud.tencent.com/document/product/1301/90227)
- [微信官方示例工程 app.json](https://github.com/wechat-miniprogram/miniprogram-demo/blob/master/miniprogram/app.json)
- [uni-app 上传文件 API](https://uniapp.dcloud.net.cn/api/request/network-file.html)

本次可访问的微信官方 API 源码及腾讯官方资料用于核查登录凭证、头像组件和隐私授权约束。实际微信公众平台的主体权限、域名和隐私指引仍需与当前账号配置一致。
