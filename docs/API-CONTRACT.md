# 初版接口契约

本文件是三个工程共同使用的实现契约。API 前缀 `/api/v1`，JSON 字段 camelCase，时间 ISO-8601 带时区（展示 Asia/Shanghai，数据库 UTC）。请求 JSON 为 `{ "data": <DTO> }`；响应为 `{ "code": "OK", "message": "success", "data": <T>, "traceId": "..." }`。错误使用非 OK 业务码和正确 HTTP 状态。Bearer token；分页 `page=1&size=10`，分页数据 `{records:[],total:0,page:1,size:10}`。公开下拉列表可用 size=100。

## 身份

- POST `/auth/login` data={username,password}，返回 `{token,user:{id,username,displayName,roles:["ADMIN"],doctorId?}}`。
- POST `/auth/demo-login` data={deviceId}。deviceId 为客户端本地保存的随机设备标识，同一标识返回同一演示患者，患者只有 PATIENT 角色。仅 local 演示开关开启可用。
- POST `/auth/logout` data={}，当前 token 作废。
- GET `/me` 返回当前 user（含 roles）。
- GET `/me/patient-profile` 返回 `{id,userId,realName,phone,gender,birthDate?,remark?}`。
- PUT `/me/patient-profile` data={realName,phone,gender?,birthDate?,remark?} 返回患者资料。
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

## 数据与实现约定

- 所有表含 deleted(0/1)、created_at、updated_at；业务无物理删除。普通查询包含 deleted=0。引用历史使用预约快照。
- booked_slots 统计 PENDING/CONFIRMED，进入所有终态时只释放一次；使用排班行锁，事务固定锁顺序（先 schedule 再 appointment），唯一活动键兜底，避免扣减与取消锁反转。
- 校验医生排班交叉；新增排班通过医生行锁串行校验；停诊/停用不能留下不可处理的活动预约。
- R.data 必须级联校验。请求 body 必填时校验 data 非空；认证过滤器错误也返回 R。
- Redis 3.2.100 使用基础命令和 RESP2；仅公开资料缓存、失败时查 MySQL；身份失效与预约一致性不依赖 Redis。
- 管理后台为 Vue3/TS/Element Plus；小程序 Vue3 uni-app 自己的兼容 Vite 工具链，使用 uni.request 并包装 R。
- 所有依赖固定实际存在的补丁版本并锁定，指定 Java/MySQL/Redis/Node 版本保持不变。
