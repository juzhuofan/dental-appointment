# 微信小程序真机调试与登录排查

本轮已修复微信响应解析和真机 API 地址两个问题。使用步骤是：重新启动 IDEA 后端，重新构建小程序，在手机验证电脑的健康检查地址，再开始真机调试。登录弹窗保持本项目页面风格，提供“微信一键登录”和“手机号登录（暂未开放）”，头像不再是登录前置条件。

## 1. 本次已确认的原因

### 真机无法连接诊所服务

原默认地址为 `http://127.0.0.1:8080/api/v1`。开发者工具运行在电脑上时可以访问电脑后端，但在手机中 `127.0.0.1` 指手机本身，无法找到电脑上的后端。

当前电脑 WLAN 地址为 `10.212.182.144`，`miniapp/.env.local` 已配置：

```dotenv
VITE_API_BASE_URL=http://10.212.182.144:8080/api/v1
```

电脑访问 `http://10.212.182.144:8080/actuator/health` 已得到 HTTP 200，状态 `UP`。用户随后在手机访问 `http://10.212.182.144:8080/api/v1/auth/wechat-config`，收到 `code: OK`、`enabled: true`、`phoneNumberEnabled: false`，已确认手机到电脑的公开登录接口连通。

手机最初返回 `UNAUTHORIZED` 也说明已到达后端。当前运行实例的 `/` 和 `/actuator/health/`（末尾多一个斜杠）返回 401，准确的 `/actuator/health` 返回 200。检查健康地址时保留完整路径且不多加尾斜杠；不需要登录才能调用公开的 `wechat-config`。

### 微信登录误报“暂时无法连接微信服务”

微信 `code2Session` 接口实际返回 HTTP 200、`Content-Type: text/plain` 和 JSON 内容。旧代码直接要求 Spring 将响应转换为 `JsonNode`，导致 `UnknownContentTypeException`，随后被统一转换成“暂时无法连接微信服务”。

修复后先读取响应文本，再用 `ObjectMapper` 解析 JSON。真实微信错误能按错误码分类，不会再将内容类型不匹配误报成网络故障。

安全探针的实际对比结果：

| 检查 | 结果 |
| --- | --- |
| Java 17 默认 JDK HTTP、HTTP/1.1、Apache HTTP、PowerShell 到微信官方接口 | 均可连接 |
| 固定无效登录 code 的微信响应 | HTTP 200、`text/plain`、微信错误码 `40029` |
| 修复前业务响应 | `WECHAT_API_UNAVAILABLE`、HTTP 502 |
| 修复后业务响应 | `WECHAT_CODE_INVALID`、HTTP 400 |
| 当前 AppID/AppSecret 调用 `stable_token` | HTTP 200，认证成功 |

探针没有使用真实用户 code、创建用户账号或保存令牌。详细的无凭证结果在 `.local/wechat-network/result.json`。这些检查不等于已完成真实微信用户登录验收。

## 2. 按顺序重新运行

1. 在 IDEA 停止当前 `DentalAppointmentApplication`，再点击运行。当前 8080 上的旧进程不会因为源文件修改就自动加载修复代码，需要由你重启。不要另外启动第二个占用 8080 的实例。
2. 在电脑浏览器打开 `http://10.212.182.144:8080/actuator/health`，确认能看到 `{"status":"UP"}`。
3. 手机与电脑连接同一个 Wi-Fi。用手机浏览器打开完全相同的健康检查地址，确认也能看到 `UP`。真机调试控制条显示“已连接”，只表示调试通道已连接，不表示手机可以访问业务后端。
4. 在 PowerShell 执行以下命令，重新生成原生小程序代码：

   ```powershell
   Set-Location 'D:\GraduationProject\dental-appointment\miniapp'
   npm run build:mp-weixin
   ```

5. 微信开发者工具打开项目目录 `D:\GraduationProject\dental-appointment\miniapp`，确认使用根目录的 `project.config.json`。实际运行代码目录为 `dist/build/mp-weixin/`。
6. 在开发者工具“详情 → 本地设置”检查开发期间的“不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书”选项。具体名称可能随工具版本变化。此选项用于开发调试，正式环境需要可访问的 HTTPS 后端和微信公众平台配置的合法服务器域名。
7. 点击“编译”，结束之前的真机调试，再重新扫码启动真机调试。`.env.local` 的修改需要第 4 步重新构建，单独点击开发者工具“编译”不会重新运行 uni-app 构建。

局域网 IP 可能在更换 Wi-Fi或重新获取地址后改变。地址改变时更新 `miniapp/.env.local`，然后重新执行 `npm run build:mp-weixin`。无需将 AppSecret 或 OSS AccessKey 放入小程序配置。

## 3. 手机健康检查不通时

先确认手机浏览器能否打开健康检查地址，再排查小程序。手机浏览器也打不开时，问题位于手机到电脑的网络链路，改登录按钮无法解决。

| 现象 | 检查方式 |
| --- | --- |
| 电脑也打不开健康检查 | 检查后端是否已启动、8080 是否正常、电脑 WLAN 地址是否改变 |
| 电脑能打开，手机不能打开 | 确认是同一个 Wi-Fi，检查访客网络、校园网络或路由器是否启用客户端/AP 隔离；同名 Wi-Fi不保证设备可相互访问 |
| 同一局域网仍超时 | 由你检查 Windows 防火墙中对 Java/8080 入站访问的允许规则；无需直接关闭整个防火墙 |
| 手机浏览器能打开，小程序仍提示域名错误 | 检查微信开发者工具调试设置和服务器域名要求，并确认重新构建后的请求地址 |
| 小程序仍请求 `127.0.0.1` | 重新执行 uni-app 构建，确认开发者工具打开了正确目录，重新开始真机调试 |

本次没有自动修改 Windows 防火墙、路由器或系统安全设置。网络隔离存在时，可换用允许设备相互访问的开发网络，或使用已部署并配置合法域名的 HTTPS 后端。

## 4. 登录流程与示例图的区别

当前个人主体模式保持 `app.wechat.phone-number-enabled: false`。进入小程序只检查已有业务 token，未登录显示登录方式弹窗，不在启动阶段调用微信登录。用户主动点击“微信一键登录”后，微信返回当前微信登录用户的一次性 `code`，后端调用微信官方接口验证 OpenID，再建立业务登录状态。

`wx.login` 识别当前微信用户，不提供“列出多个微信账号让小程序选择”的接口。用户给出的原生面板写的是“申请获取并验证你的手机号”，展示的是可授权的手机号；它是 `getPhoneNumber` 手机号授权组件，并不是微信账号选择面板。

使用该原生手机号面板需要符合微信平台的主体、认证、接口权限和额度要求。当前已确认是个人主体或尚未开通该能力，不能仅通过修改页面就弹出相同的号码选择面板，也不能创建假的账号或手机号列表。后续取得平台权限后，启用现有手机号能力分支，再用真实 `getPhoneNumber` 授权 code 在后端验证。参考 [微信手机号快速验证组件](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/getPhoneNumber.html)。

当前流程无需选择头像即可登录。头像仍可在“我的”中由用户主动设置，上传 OSS，并将稳定链接保存到用户表。平台并不通过 `wx.login` 自动提供真实头像或手机号；系统也不会自动填充虚假资料。手机号登录入口保留“暂未开放”，与未来的短信验证码登录实现区分。

## 5. 真机验收顺序

1. 首次进入或清除本地业务登录状态后，确认显示登录方式弹窗，未要求先选头像。
2. 点击“微信一键登录”，确认服务器收到微信生成的真实 code，登录成功后进入首页。不要将诊断探针的无效 code 当作成功登录凭据。
3. 确认“手机号登录（暂未开放）”是禁用入口，不触发短信或模拟登录成功。
4. 关闭再进入，确认有效登录状态可恢复；已注册账号需要重新验证时仍对应同一微信用户，不重复注册。
5. 登录后进入“我的”设置头像，确认上传成功后可显示；不设置头像时其他已授权业务仍可使用。
6. 断网或输入失效 code 时确认出现对应失败提示；网络恢复后重新获取微信 code再试，不重复使用过期 code。

如仍失败，请提供开发者工具 Network 面板中的请求地址、HTTP 状态码、响应 `code`、响应 `message`，以及后端控制台的受控错误类型。不要提供 AppSecret、AccessKey Secret、登录 code、业务 token 或含签名的完整 URL。

官方参考：[微信登录流程](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/login.html)、[code2Session](https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html)、[头像昵称填写能力](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/userProfile.html)、[网络与服务器域名](https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html)。
