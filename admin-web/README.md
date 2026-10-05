# 口腔诊所管理后台

基于 Vue 3、TypeScript、Element Plus 的本地管理工作台，管理员可维护科室、医生、账号、排班、预约、公告、诊所信息并查看患者资料及日志；医生仅能查看本人排班、本人预约，并登记已确认预约的完成或爽约状态。权限由服务端最终校验。

## 启动

使用 Node.js **24.18.0**；从项目根目录先启动后端（默认 `127.0.0.1:8080`），然后在本目录执行：

```powershell
npm ci
npm run dev
```

打开 <http://127.0.0.1:5173>，使用项目根 README 提供的本地演示账号登录。前端不保存或自动填充密码。开发服务器将 `/api` 代理到本机后端。

## 构建与类型检查

```powershell
npm run typecheck
npm run build
npm run preview
```

`dist` 为管理端构建产物。正式部署使用服务端托管 `dist`，将 `/api` 反向代理到后端，并为 history 路由配置 `index.html` 回退。Vite preview 仅用于查看产物；其 `/api` 未配置代理，如需连接后端请使用正式反向代理或构建前在本地 `.env.local` 设置后端完整地址及后端 CORS。

## 约定

- 接口遵循 [`../docs/API-CONTRACT.md`](../docs/API-CONTRACT.md)，JSON 写请求为 `{data: DTO}`，响应为 `R<T>`；HTTP 401 清除会话并回到登录页。
- 日期录入、显示统一为北京时间，提交为带 `+08:00` 的 ISO-8601 字符串。
- 列表包含搜索、分页、加载、空态、错误提示；删除、停诊与状态转换均需确认。所有删除在后端执行逻辑删除。
- 医生档案需关联角色为 `DOCTOR` 的账号；可先在账号管理中创建，再到医生档案关联。
- 依赖版本精确写入 `package.json`，解析结果由 `package-lock.json` 固定，使用 `npm ci` 复现。
- `.npmrc` 使用官方 npm 源并将下载缓存保存在本目录 `.npm-cache`，避免本机全局缓存目录权限问题。缓存已忽略，不提交。
- 本目录采用 Composition API、业务页面与公共请求/日期处理分层，TypeScript 开启严格校验。

## 固定依赖来源

- [Vue 官方发布](https://github.com/vuejs/core/releases)
- [Element Plus 官方发布](https://github.com/element-plus/element-plus/releases)
- [Vite 官方发布](https://github.com/vitejs/vite/releases)

实际依赖版本以 `package.json` 为准：Vue 3.5.43、Element Plus 2.14.7、Vite 8.3.2；Vue Router 4.6.4、Pinia 3.0.4、Axios 1.20.0；TypeScript 5.9.3。
