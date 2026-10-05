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
VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1
```

API 前缀必须包含 `/api/v1`。前端不保存 MySQL、Redis 密码或微信 AppSecret。

## H5 辅助联调

```powershell
npm.cmd run dev:h5
```

访问 `http://127.0.0.1:5174`，可以使用浏览器移动设备模式检查页面。后端需要允许该本地来源。`npm.cmd run build:h5` 生成 `dist/build/h5/`。H5 用于本地辅助联调，主要交付为微信小程序。

## 登录、请求与数据归属

- “一键登录”调用 `POST /auth/demo-login`，请求只包含 `{data:{deviceId}}`。deviceId 为随机且持久的本地设备标识，同设备保持同一演示患者。
- 服务端创建默认虚构资料；用户可以编辑姓名、手机号、性别、出生日期与备注。退出登录只清除 token/user，保留 deviceId。
- `utils/request.js` 用 `uni.request` 封装 JSON 请求，所有写请求为 `{data:DTO}`，成功响应 `R.code=OK` 后提取 `R.data`。Bearer token 自动附加；401 清除会话并导航至登录页；断网和业务错误用中文展示。
- 确认页通过公开 `GET /schedules/{id}` 核对目标号源，不依赖医生排班的第一页是否包含该时段。
- 提交预约只发送排班 ID、自己的就诊资料 ID、就诊诉求，不发送用于指定身份的 patientUserId。预约创建使用同一提交动作的 `Idempotency-Key`。网络或服务错误导致结果未知时，冻结当次 payload、幂等键和编辑控件；重试只重放原提交，页面也提供“查看我的预约”。明确业务拒绝后才清除当次提交并重新核对号源。
- 时间统一按 Asia/Shanghai 显示，日期筛选传 `yyyy-MM-dd`。号源是否有效、是否可取消、预约归属以及状态变更全部以服务端校验为准。

## 页面结构

| 页面 | 用途 |
| --- | --- |
| `pages/home` | 首页 tab，诊所、公告、科室与医生推荐 |
| `pages/appointments` | 预约 tab，本人预约筛选及分页 |
| `pages/me` | 我的 tab，资料与退出登录 |
| `pages/login` | 演示登录 |
| `pages/doctors` | 科室筛选、医生搜索 |
| `pages/doctor` | 医生详情、日期与时段选择 |
| `pages/confirm` | 资料核对及预约创建 |
| `pages/result` | 提交成功反馈 |
| `pages/appointment` | 预约详情、按规则取消 |
| `pages/profile` | 就诊资料维护 |
| `pages/notice` | 公告详情，内容按文本显示 |

页面包含加载、空列表、失败与重试状态。图形与标记为 CSS 及文本绘制，不依赖外部图像服务。

## 已执行的构建

2026-10-05：在 Node.js 24.18.0 下安装锁定依赖，并成功运行 `npm.cmd run build:mp-weixin` 和 `npm.cmd run build:h5`（uni-app Compiler 5.26 / Vue 3）。完整联调结果见项目根部署文档与验证记录。
