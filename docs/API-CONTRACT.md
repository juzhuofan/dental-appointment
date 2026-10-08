# 初版接口契约

本文件是三个工程共同使用的实现契约。API 前缀 `/api/v1`，JSON 字段 camelCase，时间 ISO-8601 带时区（展示 Asia/Shanghai，数据库 UTC）。请求 JSON 为 `{ "data": <DTO> }`；响应为 `{ "code": "OK", "message": "success", "data": <T>, "traceId": "..." }`。错误使用非 OK 业务码和正确 HTTP 状态。Bearer token；分页 `page=1&size=10`，分页数据 `{records:[],total:0,page:1,size:10}`。公开下拉列表可用 size=100。

此文档是后端与现有前端的接口基线。管理后台默认 base URL 为 `/api/v1`，小程序默认 `http://127.0.0.1:8080/api/v1`；两端的 HTTP 工具会给写请求体加上 `data` 外层、携带 Bearer token，并在收到响应后解开 `R<T>.data`。查询参数通过 URL 传递，预约幂等键通过 `Idempotency-Key` 请求头传递。

## 身份

- POST `/auth/login` data={username,password}，返回 `{token,user:{id,username,displayName,roles:["ADMIN"],doctorId?}}`。
- POST `/auth/demo-login` 为历史验证接口，默认关闭，小程序不再调用；开启时仅用于独立测试环境的旧场景复验。
- GET `/auth/wechat-config` 公开返回 `{enabled,phoneNumberEnabled}`，不返回 AppSecret。本机个人主体模式为 true / false。
- POST `/auth/wechat-login` data={loginCode,phoneCode?,register?}。`loginCode` 由真实 `wx.login` 获取，后端通过微信服务验证 OpenID；默认 register=false 仅恢复已有账号，首次返回 409 / `WECHAT_REGISTRATION_REQUIRED`。用户主动点击后 register=true 创建 PATIENT 账号。本机不调用手机号授权 API；手机号后续在就诊资料中填写。未来开通手机号能力后，phoneCode 为独立 getPhoneNumber 组件授权码。
- POST `/auth/logout` data={}，当前 token 作废。
- GET `/me` 返回当前 user=`{id,username,displayName,roles,doctorId,phone,avatarUrl,avatarDisplayUrl,wechatBound,profileCompleted}`。个人模式下微信身份认证即可 profileCompleted=true，头像可为空；就诊姓名和电话仍须在预约前填写。手机号授权模式还要求可信手机号存在。
- POST `/me/avatar`，认证后以 multipart 的 `file` 字段上传本人选择的头像，最多 2 MiB，校验图片后上传 OSS，将普通 URL 原文存入 `sys_user.avatar_url` 并更新时间，返回 `R<UserVO>`。仅微信患者可用；客户端不能直接指定任意外部地址。avatarDisplayUrl 为私有 OSS 15 分钟展示签名，不写入数据库、不改变 ACL。
- PUT `/me/account-profile` data={displayName}，仅更新当前微信患者账号的昵称并返回 `R<UserVO>`，不修改就诊人姓名。
- GET `/me/patient-profiles` 返回当前账号的有效就诊人列表，每项为 `{id,userId,isDefault,realName,phone,gender,birthDate?,remark?}`；默认档案在前。
- POST `/me/patient-profiles` data={realName,phone,gender?,birthDate?,remark?} 新增就诊人。
- PUT `/me/patient-profiles/{id}` 更新当前账号所属的指定就诊人。
- PUT `/me/patient-profiles/{id}/default` 设置默认就诊人，一个账号仅有一位有效默认档案。
- DELETE `/me/patient-profiles/{id}` 逻辑删除就诊人；最后一位或有未完成预约的就诊人不可删除。
- GET/PUT `/me/patient-profile` 保留为旧客户端兼容入口，操作默认就诊人。
- POST `/me/password` data={oldPassword,newPassword}。

## 公开信息

- GET `/clinic` 返回 `{name,phone,address,openingHours,introduction,cancelBeforeMinutes}`。
- GET `/departments` 返回 PageResult：`{id,name,description,sortOrder,status}`。
- GET `/doctors?departmentId&keyword` 返回 PageResult：`{id,userId?,departmentId,departmentName,name,title,specialty,introduction,avatarUrl,status}`。
- GET `/doctors/{id}` 返回单医生。
- GET `/schedules?doctorId&departmentId&dateFrom&dateTo` 返回 PageResult：`{id,doctorId,doctorName,departmentId,departmentName,startTime,endTime,totalSlots,bookedSlots,remainingSlots,status,cancelBeforeMinutes}`。只展示启用医生/科室下，已发布且未过期的排班。
- GET `/schedules/{id}` 返回单条可约排班（与公开列表相同的状态和关联校验），用于预约确认，避免因分页截断误判号源关闭。
- GET `/notices` 返回 PageResult：`{id,title,content,status,publishAt,expireAt,createdAt}`。

## 预约

- POST `/appointments` data={scheduleId,patientProfileId,chiefComplaint?} 返回 AppointmentVO，创建默认 PENDING，支持 Idempotency-Key 头。
- GET `/appointments/me?status` 返回 PageResult<AppointmentVO>。
- GET `/appointments/{id}` 返回 AppointmentVO，患者本人、管理员或本人接诊医生可看。
- POST `/appointments/{id}/cancel` data={reason?}；患者遵循取消截止时间，管理员可有原因地覆盖。
- AppointmentVO：`{id,appointmentNo,patientUserId,patientProfileId,scheduleId,doctorId,departmentId,patientName,patientPhone,doctorName,departmentName,startTime,endTime,chiefComplaint,status,cancelBeforeMinutes,cancelReason,cancelledAt,createdAt}`。
- 状态 PENDING→CONFIRMED/CANCELLED；CONFIRMED→COMPLETED/NO_SHOW/CANCELLED；其余终态不得再更新。

## 管理端（/admin）

ADMIN 可访问全部；DOCTOR 仅可访问本人 schedules、appointments、dashboard 及更新本人 CONFIRMED→COMPLETED/NO_SHOW。

- GET `/admin/dashboard` 返回 `{todayAppointments,pendingAppointments,totalPatients,totalDoctors,statusCounts:[{status,count}],dailyTrend:[{date,count}]}`。
- GET/POST `/admin/departments`；PUT/DELETE `/admin/departments/{id}`。SaveDTO={name,description?,sortOrder,status}。
- GET/POST `/admin/doctors`；PUT/DELETE `/admin/doctors/{id}`。SaveDTO={userId?,departmentId,name,title?,specialty?,introduction?,avatarUrl?,status}。
- GET/POST `/admin/schedules`；PUT/DELETE `/admin/schedules/{id}`。SaveDTO={doctorId,startTime,endTime,totalSlots,status,cancelBeforeMinutes?}。已有占用时禁止改医生/时间；删除/停诊需在同一事务取消活动预约并释放号源。
- PATCH `/admin/schedules/{id}/status` data={status}，DRAFT/PUBLISHED/CLOSED/CANCELLED。关闭后不接受新预约。
- GET `/admin/appointments?status&doctorId&departmentId&dateFrom&dateTo&keyword`。
- PATCH `/admin/appointments/{id}/status` data={status,reason?}。
- DELETE `/admin/appointments/{id}` 管理员逻辑删除；活动预约先取消和释放号源；保留历史行与日志。
- GET/POST `/admin/notices`；PUT/DELETE `/admin/notices/{id}`。SaveDTO={title,content,status,publishAt?,expireAt?}，status=0草稿/1发布/2撤回。
- GET `/admin/patients` 返回 PageResult<患者资料>，列表手机号脱敏。
- GET/POST `/admin/users`，PUT/DELETE `/admin/users/{id}`。SaveDTO={username,password?(新增必填),displayName,role:"ADMIN"|"DOCTOR"|"PATIENT",status}；不能删除/停用自己或最后启用管理员。
- GET `/admin/operation-logs?action&keyword` 返回 PageResult：`{id,operatorUserId,operatorName,operatorRole,action,targetType,targetId,summary,createdAt}`。
- GET/PUT `/admin/system-config` 返回/接收 ClinicDTO。

## 文件上传

- POST `/files/upload`，完整路径为 `/api/v1/files/upload`。ADMIN、DOCTOR、PATIENT 均须登录，携带 `Authorization: Bearer <token>`。
- 请求为 `multipart/form-data`，单文件字段名固定为 `file`。文件二进制通过 multipart 部分传输，不包装为 JSON `{ "data": ... }`；浏览器使用 `FormData`，小程序使用 `uni.uploadFile`。现有 JSON 请求工具不适用于此接口。
- 单文件最多 10 MiB，整个请求最多 11 MiB；文件名剥离目录后最多 255 字符。允许扩展名 `jpg/jpeg/png/gif/webp/pdf/doc/docx/xls/xlsx/txt/csv/zip`，大小写不敏感。
- 返回 `R<FileUploadVO>`，其中 data=`{url,objectKey,originalFilename,size,contentType}`。`size` 为字节数，`url` 是普通 HTTPS 地址，不加密、不含临时签名；对象名由目录日期和 UUID 生成，并禁止同名覆盖。
- 上传服务不写数据库。业务调用方把返回 `url` 原文保存到已有字段，例如 `DoctorSaveDTO.avatarUrl`，再走对应业务保存流程。
- 错误：HTTP 400 / `INVALID_ARGUMENT`（缺少文件、类型或格式不符）、401 / `UNAUTHORIZED`（未登录）、413 / `FILE_TOO_LARGE`、415 / `UNSUPPORTED_MEDIA_TYPE`（误用 JSON 等请求类型）、503 / `FILE_STORAGE_NOT_CONFIGURED`、502 / `FILE_UPLOAD_FAILED`。错误响应仍使用 `R`。
- `app.oss.enabled` 的模块默认值为 false，本机已补齐静态凭证并启用。模块不会自行开放 Bucket/Object ACL；私有对象的固定 URL 可以保存，但匿名访问会被拒绝。上传与云端读权限分别配置。
- 配置、RAM 最小权限、Java/Vue/uni-app 示例与验证边界见 [OSS 文件上传说明](OSS-UPLOAD.md)。

## 数据与实现约定

- 所有表含 deleted(0/1)、created_at、updated_at；业务无物理删除。普通查询包含 deleted=0。引用历史使用预约快照。
- booked_slots 统计 PENDING/CONFIRMED，进入所有终态时只释放一次；使用排班行锁，事务固定锁顺序（先 schedule 再 appointment），唯一活动键兜底，避免扣减与取消锁反转。
- 校验医生排班交叉；新增排班通过医生行锁串行校验；停诊/停用不能留下不可处理的活动预约。
- R.data 必须级联校验。请求 body 必填时校验 data 非空；认证过滤器错误也返回 R。
- Redis 3.2.100 使用基础命令和 RESP2；仅公开资料缓存、失败时查 MySQL；身份失效与预约一致性不依赖 Redis。
- 管理后台为 Vue3/TS/Element Plus；小程序 Vue3 uni-app 自己的兼容 Vite 工具链，使用 uni.request 并包装 R。
- 所有依赖固定实际存在的补丁版本并锁定，指定 Java/MySQL/Redis/Node 版本保持不变。

真实微信登录、小程序后台设置和人工验证步骤见 [WECHAT-LOGIN.md](WECHAT-LOGIN.md)。注册时就诊姓名与未授权电话留空，预约前由用户填写，不生成虚构个人资料。新增 V3 迁移使微信 OpenID 及活动 OpenID 唯一索引区分大小写。

客户端启动仅检查已有 token，不自动调用微信登录；点击微信登录按钮后才取得 code 并发起认证。头像上传为个人中心的独立可选操作。
