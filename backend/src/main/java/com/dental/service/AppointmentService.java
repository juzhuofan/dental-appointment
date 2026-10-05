package com.dental.service;

import static com.dental.common.DataValues.*;

import com.dental.common.AppointmentState;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.persistence.BusinessMapper;
import com.dental.security.CurrentUser;
import com.dental.web.Requests;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AppointmentService {
    private static final List<String> ACTIVE = List.of("PENDING", "CONFIRMED");
    private static final List<String> STATES = AppointmentState.names();
    private final StoreService store;
    private final BusinessMapper mapper;
    private final ScheduleService schedules;

    public AppointmentService(
            StoreService store, BusinessMapper mapper, ScheduleService schedules) {
        this.store = store;
        this.mapper = mapper;
        this.schedules = schedules;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Requests.Appointment request, String idempotencyKey) {
        var user = CurrentUser.get();
        if (!user.isPatient()) {
            throw BusinessException.forbidden();
        }
        // 账号停用与预约创建串行，避免已通过认证的并发请求在停用后留下新预约。
        var account = store.require("sys_user", user.id(), true);
        if (integer(account.get("status")) != 1 || !mapper.roles(user.id()).contains("PATIENT")) {
            throw BusinessException.forbidden();
        }
        Map<String, Object> idempotency = null;
        if (idempotencyKey != null) {
            if (!idempotencyKey.matches("[A-Za-z0-9_.:-]{8,100}")) {
                throw BusinessException.bad("Idempotency-Key 必须为8–100位字母、数字或指定符号");
            }
            String hash =
                    AuthService.sha256(
                            request.scheduleId()
                                    + ":"
                                    + request.patientProfileId()
                                    + ":"
                                    + (request.chiefComplaint() == null
                                            ? ""
                                            : request.chiefComplaint()));
            mapper.reserveIdempotency(user.id(), idempotencyKey, hash);
            idempotency = mapper.lockIdempotency(user.id(), idempotencyKey);
            if (!hash.equals(idempotency.get("requestHash"))) {
                throw BusinessException.conflict("IDEMPOTENCY_CONFLICT", "同一幂等键不能用于不同预约请求");
            }
            if (idempotency.get("appointmentId") != null) {
                return detail(number(idempotency.get("appointmentId")));
            }
        }
        // 预约写事务统一先锁排班，随后才允许锁预约。
        var schedule = store.require("doctor_schedule", request.scheduleId(), true);
        var profile = store.require("patient_profile", request.patientProfileId(), false);
        if (number(profile.get("userId")) != user.id()) {
            throw BusinessException.forbidden();
        }
        if (profile.get("realName") == null || profile.get("phone") == null) {
            throw BusinessException.bad("请先完善就诊人资料");
        }
        var doctor = store.require("doctor", number(schedule.get("doctorId")), false);
        var department = store.require("department", number(schedule.get("departmentId")), false);
        if (!"PUBLISHED".equals(schedule.get("status"))
                || !time(schedule.get("startTime")).isAfter(now())
                || integer(doctor.get("status")) != 1
                || integer(department.get("status")) != 1) {
            throw BusinessException.conflict("SCHEDULE_UNAVAILABLE", "该排班未开放或已过期");
        }
        String activeKey = user.id() + ":" + request.scheduleId();
        if (store.findOne("appointment", fields("appointmentActiveKey", activeKey)) != null) {
            throw BusinessException.conflict("DUPLICATE_APPOINTMENT", "您已预约该时段");
        }
        if (mapper.occupy(request.scheduleId()) != 1) {
            throw BusinessException.conflict("APPOINTMENT_FULL", "该时段号源已满");
        }
        var values =
                fields(
                        "appointmentNo",
                        "AP" + UUID.randomUUID().toString().replace("-", "").substring(0, 30),
                        "patientUserId",
                        user.id(),
                        "patientProfileId",
                        request.patientProfileId(),
                        "scheduleId",
                        request.scheduleId(),
                        "doctorId",
                        schedule.get("doctorId"),
                        "departmentId",
                        schedule.get("departmentId"),
                        "patientNameSnapshot",
                        profile.get("realName"),
                        "patientPhoneSnapshot",
                        profile.get("phone"),
                        "doctorNameSnapshot",
                        doctor.get("name"),
                        "departmentNameSnapshot",
                        department.get("name"),
                        "startTimeSnapshot",
                        schedule.get("startTime"),
                        "endTimeSnapshot",
                        schedule.get("endTime"),
                        "chiefComplaint",
                        request.chiefComplaint(),
                        "status",
                        "PENDING",
                        "appointmentActiveKey",
                        activeKey);
        long id;
        try {
            id = store.insert("appointment", values);
        } catch (DuplicateKeyException exception) {
            throw BusinessException.conflict("DUPLICATE_APPOINTMENT", "您已预约该时段");
        }
        if (idempotency != null) {
            store.update(
                    "appointment_idempotency",
                    number(idempotency.get("id")),
                    fields("appointmentId", id));
        }
        store.audit("APPOINTMENT_CREATE", "appointment", id, "患者提交预约");
        return detail(id);
    }

    public Map<String, Object> detail(long id) {
        var row = store.require("appointment", id, false);
        authorize(row);
        return appointmentView(row);
    }

    private void authorize(Map<String, Object> row) {
        var user = CurrentUser.get();
        if (user.isAdmin()) {
            return;
        }
        if (user.isDoctor()
                && user.doctorId() != null
                && number(row.get("doctorId")) == user.doctorId()) {
            return;
        }
        if (user.isPatient() && number(row.get("patientUserId")) == user.id()) {
            return;
        }
        throw BusinessException.forbidden();
    }

    public Map<String, Object> appointmentView(Map<String, Object> row) {
        var user = CurrentUser.get();
        String phone = (String) row.get("patientPhoneSnapshot");
        var schedule = mapper.find("doctor_schedule", number(row.get("scheduleId")), false);
        return view(
                fields(
                        "id",
                        row.get("id"),
                        "appointmentNo",
                        row.get("appointmentNo"),
                        "patientUserId",
                        row.get("patientUserId"),
                        "patientProfileId",
                        row.get("patientProfileId"),
                        "scheduleId",
                        row.get("scheduleId"),
                        "doctorId",
                        row.get("doctorId"),
                        "departmentId",
                        row.get("departmentId"),
                        "patientName",
                        row.get("patientNameSnapshot"),
                        "patientPhone",
                        user.isPatient() && number(row.get("patientUserId")) == user.id()
                                ? phone
                                : maskPhone(phone),
                        "doctorName",
                        row.get("doctorNameSnapshot"),
                        "departmentName",
                        row.get("departmentNameSnapshot"),
                        "startTime",
                        row.get("startTimeSnapshot"),
                        "endTime",
                        row.get("endTimeSnapshot"),
                        "chiefComplaint",
                        row.get("chiefComplaint"),
                        "status",
                        row.get("status"),
                        "cancelReason",
                        row.get("cancelReason"),
                        "cancelledAt",
                        row.get("cancelledAt"),
                        "createdAt",
                        row.get("createdAt"),
                        "cancelBeforeMinutes",
                        schedule == null ? 120 : schedule.get("cancelBeforeMinutes")));
    }

    public PageResult<Map<String, Object>> mine(String status, int page, int size) {
        validateStatus(status);
        return CatalogService.mappedPage(
                store.page(
                        "appointment",
                        fields("patientUserId", CurrentUser.get().id(), "status", status),
                        null,
                        false,
                        page,
                        size),
                this::appointmentView);
    }

    public PageResult<Map<String, Object>> adminList(
            String status,
            Long doctorId,
            Long departmentId,
            String from,
            String to,
            String keyword,
            int page,
            int size) {
        validateStatus(status);
        var filter = fields("status", status, "doctorId", doctorId, "departmentId", departmentId);
        dateFilters(filter, "startTimeSnapshot", from, to);
        if (!CurrentUser.get().isAdmin()) {
            filter.put("doctorId", schedules.requireDoctorId());
        }
        return CatalogService.mappedPage(
                store.page("appointment", filter, keyword, false, page, size),
                this::appointmentView);
    }

    private static void validateStatus(String status) {
        if (status != null && !status.isBlank() && !STATES.contains(status)) {
            throw BusinessException.bad("预约状态不正确");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancel(long id, Requests.Cancel request) {
        var prior = store.require("appointment", id, false);
        authorize(prior);
        var schedule = store.require("doctor_schedule", number(prior.get("scheduleId")), true);
        var row = store.require("appointment", id, true);
        authorize(row);
        var user = CurrentUser.get();
        if (!user.isAdmin()
                && (!user.isPatient() || number(row.get("patientUserId")) != user.id())) {
            throw BusinessException.forbidden();
        }
        if (!ACTIVE.contains(row.get("status"))) {
            throw BusinessException.conflict("APPOINTMENT_NOT_CANCELLABLE", "预约已结束或已取消");
        }
        if (user.isAdmin()) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw BusinessException.bad("管理员取消预约必须填写原因");
            }
        } else if (!now().isBefore(
                        time(schedule.get("startTime"))
                                .minusMinutes(integer(schedule.get("cancelBeforeMinutes"))))) {
            throw BusinessException.conflict("CANCEL_DEADLINE_PASSED", "已超过取消截止时间，请联系诊所");
        }
        transition(row, "CANCELLED", request.reason());
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> status(long id, Requests.AppointmentStatus request) {
        var prior = store.require("appointment", id, false);
        authorize(prior);
        store.require("doctor_schedule", number(prior.get("scheduleId")), true);
        var row = store.require("appointment", id, true);
        authorize(row);
        var user = CurrentUser.get();
        String old = row.get("status").toString();
        String target = request.status();
        if (!user.isAdmin()) {
            if (!user.isDoctor()
                    || !"CONFIRMED".equals(old)
                    || !List.of("COMPLETED", "NO_SHOW").contains(target)) {
                throw BusinessException.forbidden();
            }
        }
        if (!AppointmentState.valueOf(old).allows(AppointmentState.valueOf(target))) {
            throw BusinessException.conflict("INVALID_STATUS_TRANSITION", "当前预约状态不允许此操作");
        }
        if ("CANCELLED".equals(target)
                && (request.reason() == null || request.reason().isBlank())) {
            throw BusinessException.bad("取消预约必须填写原因");
        }
        transition(row, target, request.reason());
        return detail(id);
    }

    /** 调用者已锁定排班和预约。只允许活动态进入终态时释放一次。 */
    private void transition(Map<String, Object> row, String target, String reason) {
        long id = number(row.get("id"));
        var values =
                fields("status", target, "handledBy", CurrentUser.get().id(), "handledAt", now());
        if (!ACTIVE.contains(target)) {
            values.put("appointmentActiveKey", null);
            if ("CANCELLED".equals(target)) {
                values.put("cancelReason", reason);
                values.put("cancelledAt", now());
            }
        }
        store.update("appointment", id, values);
        if (ACTIVE.contains(row.get("status"))
                && !ACTIVE.contains(target)
                && mapper.release(number(row.get("scheduleId"))) != 1) {
            throw new IllegalStateException("Slot conservation violation");
        }
        store.audit("APPOINTMENT_" + target, "appointment", id, "变更预约处理状态");
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        var prior = store.require("appointment", id, false);
        store.require("doctor_schedule", number(prior.get("scheduleId")), true);
        var row = store.require("appointment", id, true);
        if (ACTIVE.contains(row.get("status"))) {
            transition(row, "CANCELLED", "管理员逻辑删除预约");
        }
        store.softDelete("appointment", id);
        store.audit("APPOINTMENT_DELETE", "appointment", id, "逻辑删除预约，保留历史行");
    }

    public Map<String, Object> dashboard() {
        Long doctorId = CurrentUser.get().isAdmin() ? null : schedules.requireDoctorId();
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        var todayFilter = fields("doctorId", doctorId);
        dateFilters(todayFilter, "startTimeSnapshot", today.toString(), today.toString());
        long totalPatients =
                doctorId == null
                        ? mapper.count("patient_profile", fields(), null, false)
                        : mapper.doctorPatientCount(doctorId);
        return fields(
                "todayAppointments",
                mapper.count("appointment", todayFilter, null, false),
                "pendingAppointments",
                mapper.count(
                        "appointment",
                        fields("status", "PENDING", "doctorId", doctorId),
                        null,
                        false),
                "totalPatients",
                totalPatients,
                "totalDoctors",
                doctorId == null ? mapper.count("doctor", fields("status", 1), null, false) : 1,
                "statusCounts",
                mapper.statusCounts(doctorId),
                "dailyTrend",
                mapper
                        .dailyTrend(
                                doctorId,
                                today.minusDays(6)
                                        .atStartOfDay(CLINIC_ZONE)
                                        .withZoneSameInstant(ZoneOffset.UTC)
                                        .toLocalDateTime())
                        .stream()
                        .map(com.dental.common.DataValues::view)
                        .toList());
    }

    /** 患者账号停用时只取消该患者的预约，不影响同排班其他患者。 */
    public void cancelPatient(long userId) {
        for (long scheduleId : mapper.patientActiveScheduleIds(userId)) {
            store.require("doctor_schedule", scheduleId, true);
            for (long id : mapper.activePatientAppointmentIds(scheduleId, userId)) {
                var row = store.require("appointment", id, true);
                if (ACTIVE.contains(row.get("status"))) {
                    transition(row, "CANCELLED", "患者账号停用或删除");
                }
            }
        }
    }
}
