# 三端第二轮复查（2026-10-15）

基于六项修复后的当前工作区复查。本轮只检查，未实施新的业务修复。上轮修复记录见 [六项问题修复与验证](FIX-VERIFICATION-2026-10-15.md)。

本轮再次运行客户端逻辑回归，65 项全部通过；另外以实际代码和内存边界模拟复现了下列新问题。这些场景尚未纳入现有正式回归，不能用已有测试通过替代对应边界验证。

## 需要修复的问题

### 1. 高优先级：密码登录可能覆盖刚完成的账号停用或密码重置

- 密码登录先无锁读取整个账号并校验密码；签发令牌时，又把这个旧账号对象通过 `updateById` 全字段写回。
- 如果管理员在这两步之间完成停用或密码重置，迟到的登录写回可能恢复 `status=1` 与旧密码哈希，并签发一枚新的活动令牌。
- 位置：[AuthService.java](D:/GraduationProject/dental-appointment/backend/src/main/java/com/dental/auth/service/AuthService.java:78)、[签发时的写回](D:/GraduationProject/dental-appointment/backend/src/main/java/com/dental/auth/service/AuthService.java:194)。
- 建议：密码登录锁定当前账号后确认状态和凭据；记录登录时间时只更新专用字段。需覆盖登录与改密码、停用、角色修改的交错。
- 证据：实际 `AuthService`、模拟持久层穿插和真实 MyBatis 生成 SQL。SQL 确实更新密码和状态，不只是登录时间。未执行真实数据库并发测试。
- 内部复现：`.local/audit-repros/LoginStaleWriteRepro.java`。

### 2. 账号资料保存和个人中心仍缺少成功响应的会话归属保护

- “我的”页 A 账号请求在途，建立 B 会话后，A 的迟到成功响应仍执行 `saveUser(A)` 并展示 A 的就诊人；B 的令牌却仍保留。
- 资料编辑器中，A 的昵称保存等待期间会话切换，旧响应仍回写 A，再继续调用头像上传；该次上传在调用时取得 B 的当前令牌，可能将 A 选中的图片写入 B 的头像。
- 这与已修复的迟到 401 不同：本次是成功响应及后续异步步骤没有携带原会话上下文。后端仍按提供的令牌识别身份，并非服务端绕过了身份校验。
- 位置：[个人中心](D:/GraduationProject/dental-appointment/miniapp/src/pages/me/index.vue:46)、[资料保存流程](D:/GraduationProject/dental-appointment/miniapp/src/components/AccountProfileEditor.vue:191)。
- 建议：整个读取/保存流程捕获会话代次及组件代次，每次 await 后检查归属；过期结果不能写全局用户、继续上传或显示新会话的成功提示。
- 证据：实际 SFC 与 session 模块的内存复现；包含旧编辑器继续调用上传的令牌和图片路径检查。

### 3. 管理端仍会被旧请求的迟到 401 踢出新会话

- 后台请求层遇到任何 401，都会删除当前 token 并触发退出回调，没有判断请求使用的 token 是否仍属于当前会话。
- 退出后重新登录，旧请求返回 401，新的有效登录也会退出。
- 位置：[http.ts](D:/GraduationProject/dental-appointment/admin-web/src/api/http.ts:41)、[退出回调](D:/GraduationProject/dental-appointment/admin-web/src/main.ts:16)。
- 建议：将小程序已采用的会话归属保护落实到管理端；同时防止旧请求弹出误导性的登录过期提示。
- 证据：实际转译执行 http.ts，以内存请求和存储模拟，确认新 token 被删除、退出回调被调用。

### 4. 医生改为其他角色会取消预约，但界面没有提示

- 账号状态保持启用，把医生角色改为管理员时，界面没有影响确认。
- 后端将失去医生角色视为关联业务停用，会关闭排班、取消待确认/已确认预约，并解除医生账号与档案的关联。
- 位置：[界面确认条件](D:/GraduationProject/dental-appointment/admin-web/src/views/ManagementView.vue:164)、[角色移除处理](D:/GraduationProject/dental-appointment/backend/src/main/java/com/dental/user/service/UserService.java:433)、[医生关联停用](D:/GraduationProject/dental-appointment/backend/src/main/java/com/dental/doctor/service/DoctorUserDeactivationHandler.java:42)。
- 建议：角色变更前明确展示停诊与取消预约的影响；若需要管理员兼任医生，应另行确定多角色模型，不能把单角色替换当作无影响的权限升级。
- 证据：实际管理页面 save 函数的内存复现显示确认次数为 0；后端后果由服务调用链与活动预约 SQL 核对。

### 5. AI 历史首次加载失败后，发送成功会隐藏原历史入口

- 首次会话列表读取失败后，用户仍可发送新消息；发送成功会清除错误并设置 `initialized=true`，但原历史列表从未读取成功，`conversationHasMore` 仍为 false。
- 后续重新进入该缓存页面不再请求列表，只看到新会话。旧记录仍在数据库，没有证据表明数据被删除。
- 位置：[发送时清除错误](D:/GraduationProject/dental-appointment/miniapp/src/utils/ai-chat.js:89)、[发送成功设置初始化状态](D:/GraduationProject/dental-appointment/miniapp/src/utils/ai-chat.js:107)。
- 建议：历史列表初始化状态与消息发送状态分开维护；历史加载失败后仍保留列表刷新入口。
- 证据：内存复现中历史列表请求次数保持 1，仅显示新会话，且没有更多入口或错误提示。

## 需要明确后再实施的业务规则

| 项目 | 当前情况 | 建议 |
| --- | --- | --- |
| 完成接诊与爽约时间 | 未来已确认预约也能立即完成或标记未到诊 | 明确允许操作的最早时间，并在服务端校验；需要纠错时另设明确流程 |
| 逾期未处理预约 | 没有自动迁移任务，旧 PENDING/CONFIRMED 仍阻止删除就诊人；患者又已超过自行取消截止 | 先增加后台“逾期未处理”待办，再确定人工处理或自动关闭规则 |
| 重复档案与时间冲突 | 防重依赖档案 ID，同名同电话可建多个档案；同一档案可约不同医生的重叠时段 | 先确定实际就诊人的识别和冲突规则；家属可能共用电话，不宜直接设手机号唯一 |
| 预约与取消截止 | 共用 cancelBeforeMinutes，默认提前 120 分钟既不能取消也不能新约 | 若希望临近仍可预约，应拆分两项配置 |

相关位置：[状态更新](D:/GraduationProject/dental-appointment/backend/src/main/java/com/dental/appointment/service/AppointmentService.java:237)、[删档检查](D:/GraduationProject/dental-appointment/backend/src/main/java/com/dental/user/service/UserService.java:291)、[活动预约计数](D:/GraduationProject/dental-appointment/backend/src/main/resources/mapper/PatientProfileMapper.xml:18)。这些规则缺口不是上轮六项修复引入的问题。

## 验收和后续完善

- 原 `scripts/Verify-Local.ps1` 仍运行依赖旧演示登录的完整接口/页面测试；`tests/ui/miniapp.spec.mjs:64` 仍等待旧接口和按钮。应更新完整端到端验证环境；不能为了跑通测试重新开放产品演示登录。
- 已新增的 `test:unit`、`test:regression-ui` 可验证当前逻辑和模拟界面，但不代替真实微信与独立测试数据库的全流程验收。
- 医生端优先做“今日接诊”，管理员端优先做排班模板、中文操作日志筛选；患者端可补预约须知、到院导航、再次预约。
- 医生、科室、账号下拉框仍只取首 100 条，规模扩大时需远程搜索或完整分页。
- 应核验密码登录失败限制、上传频次/容量治理和备份恢复；仓库未见这些完整流程，网关侧是否已有保护本轮未知。
- 仓库的小程序生产 API 仍为 HTTP IP，部署文档列有 HTTPS、反向代理及微信合法域名待办。本轮未探测云服务器，不能据此断言云端当前状态。

## 本轮证据与范围

- `npm.cmd run test:unit`：65 项通过，0 失败。输出 `.local/round2-unit-tests.log`。
- `.local/audit-repros/round2-admin.mjs`：实际后台请求模块和页面保存函数，2 项内存复现。
- `.local/audit-repros/round2-miniapp.mjs`：实际小程序组件/会话/聊天模块，3 项内存复现。
- `.local/audit-repros/LoginStaleWriteRepro.java`：实际密码登录服务及 MyBatis SQL，1 项确定性模拟穿插。

本轮没有修改业务源码、没有永久新增测试，也没有访问真实微信、OSS、付费模型或业务数据库。优先建议处理密码登录全字段回写、资料保存会话归属和管理端会话失效，再处理角色变更提示、AI 历史初始化与完整验收。
