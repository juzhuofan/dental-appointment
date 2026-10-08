# 口腔诊所预约 · 微信小程序

基于 uni-app Vue 3 的患者端。包含诊所首页、公告、科室筛选与医生搜索、医生详情及未来 14 天号源、就诊资料、预约提交结果、本人预约筛选与详情、取消预约和个人中心。

## 固定工具链

| 项目 | 版本 |
| --- | --- |
| Node.js | 24.18.0 |
| uni-app / uni-components / uni-h5 / uni-mp-weixin / uni-cli-shared / vite-plugin-uni | 3.0.0-5020620260917001 |
| Vue / @vue/runtime-core | 3.4.21 |
| Vite | 5.2.8 |
| Rollup | 4.14.3 |
| @dcloudio/types | 3.4.31（uni-app 当前版本的 peer 要求） |

本工程参考 [DCloud 官方 uni-preset-vue 的 vite 模板](https://github.com/dcloudio/uni-preset-vue/tree/vite)。小程序使用自己的兼容构建链；依赖及解析结果固定在 `package.json`、`package-lock.json`。所有 `@dcloudio/uni-*` 编译包使用同一完整 build 版本。

## 启动微信开发者工具

先启动本地后端（默认 `http://127.0.0.1:8080/api/v1`），再在本目录执行：

```powershell
npm.cmd ci
npm.cmd run build:mp-weixin
```

在微信开发者工具导入本工程 `miniapp` 目录（读取根 `project.config.json`，其 `miniprogramRoot` 指向 `dist/build/mp-weixin/`）；也可直接导入生成的 `dist/build/mp-weixin/` 目录。公开 AppID 已配置为 `wxd024dd8ce6863e5a`。使用“编译”即可在模拟器内操作。

持续开发时执行 `npm.cmd run dev:mp-weixin`，并导入 `dist/dev/mp-weixin/` 目录。

本地模拟器采用 `urlCheck: false`。真机中的 `127.0.0.1` 是手机本身，如需真机调试，将 API 改为电脑的局域网地址，并为后端设置可访问的网络监听及开发者工具相应调试配置。

## 配置 API 地址

复制 `.env.example` 为 `.env.local`，编辑后重建：

```dotenv
VITE_API_BASE_URL=http://10.212.182.144:8080/api/v1
```

API 前缀必须包含 `/api/v1`。前端不保存 MySQL、Redis 密码或微信 AppSecret。

## H5 辅助联调

```powershell
npm.cmd run dev:h5
```

访问 `http://127.0.0.1:5174`，可以使用浏览器移动设备模式检查公开页面。后端需要允许该本地来源。`npm.cmd run build:h5` 生成 `dist/build/h5/`。真实微信登录仅在微信小程序中执行，H5 会显示提示，不会生成演示账号。

## 登录、请求与数据归属

- 首页与登录页复用 `components/LoginDialog.vue`。未登录用户可以关闭弹窗浏览诊所、医生、号源和公告；受保护页面先等待登录初始化，再进入登录页。
- `utils/auth.js` 的 `bootstrapAuth()` 共用一个 Promise。已有 token 调用 `GET /me` 验证微信账号；无 token 或 token 过期时显示登录选项，不在启动时调用微信登录或创建账号。个人模式不要求头像或手机号存在。
- 用户主动点击后，`uni.login({provider:'weixin'})` 取得真实微信 code，再调用 `POST /auth/wechat-login`，请求为 `{data:{loginCode,register:true,phoneCode?}}`。loginCode 与手机号授权 phoneCode 分别取得，不接受客户端自报 OpenID 替代微信验证。
- 个人主体当前 `GET /auth/wechat-config` 返回 `phoneNumberEnabled:false`，使用普通微信登录按钮，不调用 `getPhoneNumber`。将来后端启用该能力时自动显示原生手机号授权按钮。独立的“手机号登录”入口暂未开放。
- 用户确认当前微信账号后登录；微信小程序不会弹出多微信账号选择列表。登录成功后“我的”页面显示账号昵称，`AccountProfileEditor.vue` 支持微信建议昵称或手动输入，并通过 `AvatarPicker.vue` 的原生 `chooseAvatar` 按钮选择头像。头像临时路径使用 `uni.uploadFile` 上传到 `POST /me/avatar`；昵称和头像均不作为登录条件。
- 微信登录凭证只用于确认账号身份，不会自动带回昵称或头像。若账号资料尚未完善，登录后会直接进入“我的”并打开资料设置：点击 `type="nickname"` 输入框选择微信建议昵称或手动输入，点击 `chooseAvatar` 按钮选择头像。保存后由后端持久化，下次登录通过 `/me` 读取。若真机仍显示旧页面或出现 `api.profiles is not a function`，重新执行 `npm.cmd run build:mp-weixin`，确认微信开发者工具导入的工程指向本目录，再清除工具缓存并重新发起真机调试。
- 就诊人管理支持一个账号维护多位就诊人、设置默认档案、编辑和逻辑删除，预约时选择实际就诊人。
- `wx.getPrivacySetting` 返回需要授权时，先显示原生 `agreePrivacyAuthorization` 按钮。只有其真实事件回调可以确认平台隐私授权；普通复选框不代替微信授权。开发者工具基础库需支持头像选择和隐私 API（至少 2.32.3），并在微信公众平台配置与实际用途一致的隐私保护指引。
- 就诊姓名、联系电话、性别、出生日期与备注通过就诊资料页面填写。个人主体登录不要求微信手机号，但预约提交仍需有效联系电话。退出登录清除本机 token/user，再次验证同一个微信身份可继续查看本人资料与预约。
- `utils/request.js` 用 `uni.request` 封装 JSON 请求，写请求为 `{data:DTO}`，成功响应 `R.code=OK` 后提取 `R.data`。Bearer token 自动附加；受保护请求 401 清除会话并导航至登录页。`auth:false` 的公开接口 401 不操作已有会话，初始化请求使用 `redirectOn401:false` 避免重复导航。
- 确认页通过公开 `GET /schedules/{id}` 核对目标号源，不依赖医生排班的第一页是否包含该时段。
- 提交预约只发送排班 ID、自己的就诊资料 ID、就诊诉求，不发送用于指定身份的 patientUserId。预约创建使用同一提交动作的 `Idempotency-Key`。网络或服务错误导致结果未知时，冻结当次 payload、幂等键和编辑控件；重试只重放原提交，页面也提供“查看我的预约”。明确业务拒绝后才清除当次提交并重新核对号源。
- 时间统一按 Asia/Shanghai 显示，日期筛选传 `yyyy-MM-dd`。号源是否有效、是否可取消、预约归属以及状态变更全部以服务端校验为准。

## 页面结构

| 页面 | 用途 |
| --- | --- |
| `pages/home` | 首页 tab，诊所、公告、科室与医生推荐 |
| `pages/appointments` | 预约 tab，本人预约筛选及分页 |
| `pages/me` | 我的 tab，资料与退出登录 |
| `pages/login` | 真实微信身份登录及用户确认 |
| `pages/doctors` | 科室筛选、医生搜索 |
| `pages/doctor` | 医生详情、日期与时段选择 |
| `pages/confirm` | 资料核对及预约创建 |
| `pages/result` | 提交成功反馈 |
| `pages/appointment` | 预约详情、按规则取消 |
| `pages/profile` | 就诊资料维护 |
| `pages/notice` | 公告详情，内容按文本显示 |

页面包含加载、空列表、失败与重试状态。图形与标记为 CSS 及文本绘制，个人头像由 OSS 提供。

## 已执行的构建

2026-10-05：在 Node.js 24.18.0 下安装锁定依赖，并成功运行 `npm.cmd run build:mp-weixin` 和 `npm.cmd run build:h5`（uni-app Compiler 5.26 / Vue 3）。完整联调结果见项目根部署文档与验证记录。
