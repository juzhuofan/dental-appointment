package com.dental.service;

import static com.dental.common.DataValues.*;

import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.persistence.BusinessMapper;
import com.dental.security.CurrentUser;
import com.dental.web.Requests;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogService.class);
    private static final String CLINIC_CACHE = "dental:v1:clinic";
    private static final Map<String, String> CLINIC_KEYS =
            Map.of(
                    "name",
                    "clinic.name",
                    "phone",
                    "clinic.phone",
                    "address",
                    "clinic.address",
                    "openingHours",
                    "clinic.openingHours",
                    "introduction",
                    "clinic.introduction",
                    "cancelBeforeMinutes",
                    "appointment.cancel_before_minutes");
    private final StoreService store;
    private final BusinessMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public CatalogService(
            StoreService store,
            BusinessMapper mapper,
            StringRedisTemplate redis,
            ObjectMapper json) {
        this.store = store;
        this.mapper = mapper;
        this.redis = redis;
        this.json = json;
    }

    public Map<String, Object> clinic() {
        try {
            String cached = redis.opsForValue().get(CLINIC_CACHE);
            if (cached != null) {
                return json.readValue(cached, new TypeReference<Map<String, Object>>() {});
            }
        } catch (DataAccessException
                | com.fasterxml.jackson.core.JsonProcessingException exception) {
            LOGGER.debug("Clinic cache read failed; querying database");
        }
        var result = clinicFromDatabase();
        try {
            redis.opsForValue()
                    .set(CLINIC_CACHE, json.writeValueAsString(result), Duration.ofSeconds(60));
        } catch (DataAccessException
                | com.fasterxml.jackson.core.JsonProcessingException exception) {
            LOGGER.debug("Clinic cache write failed");
        }
        return result;
    }

    public Map<String, Object> clinicFromDatabase() {
        var output = new LinkedHashMap<String, Object>();
        CLINIC_KEYS.forEach(
                (field, key) -> {
                    var row = store.findOne("system_config", fields("configKey", key));
                    if (row == null) {
                        throw new IllegalStateException("Missing clinic configuration");
                    }
                    output.put(
                            field,
                            "cancelBeforeMinutes".equals(field)
                                    ? Integer.valueOf(row.get("configValue").toString())
                                    : row.get("configValue"));
                });
        return output;
    }

    public int cancelBeforeMinutes() {
        return (Integer) clinicFromDatabase().get("cancelBeforeMinutes");
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveClinic(Requests.Clinic request) {
        var values =
                fields(
                        "name",
                        request.name(),
                        "phone",
                        request.phone(),
                        "address",
                        request.address(),
                        "openingHours",
                        request.openingHours(),
                        "introduction",
                        request.introduction(),
                        "cancelBeforeMinutes",
                        request.cancelBeforeMinutes());
        values.forEach(
                (field, value) -> {
                    var row =
                            store.findOne(
                                    "system_config", fields("configKey", CLINIC_KEYS.get(field)));
                    store.update(
                            "system_config",
                            number(row.get("id")),
                            fields(
                                    "configValue",
                                    value.toString(),
                                    "updatedBy",
                                    CurrentUser.get().id()));
                });
        store.audit("CLINIC_UPDATE", "system_config", 0, "更新诊所公开信息及预约取消规则");
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            redis.delete(CLINIC_CACHE);
                        } catch (DataAccessException exception) {
                            LOGGER.debug("Clinic cache invalidation failed; TTL will expire");
                        }
                    }
                });
        return clinicFromDatabase();
    }

    public PageResult<Map<String, Object>> departments(
            boolean publicOnly, String keyword, Integer status, int page, int size) {
        return mappedPage(
                store.page("department", fields("status", status), keyword, publicOnly, page, size),
                this::departmentView);
    }

    public Map<String, Object> departmentView(Map<String, Object> row) {
        return fields(
                "id",
                row.get("id"),
                "name",
                row.get("name"),
                "description",
                row.get("description"),
                "sortOrder",
                row.get("sortOrder"),
                "status",
                row.get("status"));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveDepartment(Long id, Requests.Department request) {
        if (id != null) {
            store.require("department", id, true);
        }
        var values =
                fields(
                        "name",
                        request.name().trim(),
                        "description",
                        request.description(),
                        "sortOrder",
                        request.sortOrder(),
                        "status",
                        request.status());
        long targetId = id == null ? store.insert("department", values) : id;
        if (id != null) {
            if (request.status() == 0) {
                cascadeDepartment(id, "科室停用");
            }
            store.update("department", id, values);
        }
        store.audit(
                id == null ? "DEPARTMENT_CREATE" : "DEPARTMENT_UPDATE",
                "department",
                targetId,
                "维护科室资料");
        return departmentView(store.require("department", targetId, false));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDepartment(long id) {
        store.require("department", id, true);
        cascadeDepartment(id, "科室删除");
        for (long doctorId : mapper.departmentDoctorIds(id)) {
            store.update("doctor", doctorId, fields("status", 0));
        }
        store.softDelete("department", id);
        store.audit("DEPARTMENT_DELETE", "department", id, "逻辑删除科室并取消活动预约");
    }

    private void cascadeDepartment(long id, String reason) {
        for (long doctorId : mapper.departmentDoctorIds(id)) {
            store.require("doctor", doctorId, true);
            cascadeDoctor(doctorId, reason);
        }
    }

    public PageResult<Map<String, Object>> doctors(
            boolean publicOnly,
            Long departmentId,
            String keyword,
            Integer status,
            int page,
            int size) {
        return mappedPage(
                store.page(
                        "doctor",
                        fields("departmentId", departmentId, "status", status),
                        keyword,
                        publicOnly,
                        page,
                        size),
                this::doctorView);
    }

    public Map<String, Object> doctor(long id, boolean publicOnly) {
        var row = store.require("doctor", id, false);
        var department = mapper.find("department", number(row.get("departmentId")), false);
        if (publicOnly
                && (integer(row.get("status")) != 1
                        || department == null
                        || integer(department.get("status")) != 1)) {
            throw BusinessException.missing();
        }
        return doctorView(row);
    }

    public Map<String, Object> doctorView(Map<String, Object> row) {
        var department = mapper.find("department", number(row.get("departmentId")), false);
        return fields(
                "id",
                row.get("id"),
                "userId",
                row.get("userId"),
                "departmentId",
                row.get("departmentId"),
                "departmentName",
                department == null ? "已删除科室" : department.get("name"),
                "name",
                row.get("name"),
                "title",
                row.get("title"),
                "specialty",
                row.get("specialty"),
                "introduction",
                row.get("introduction"),
                "avatarUrl",
                row.get("avatarUrl"),
                "status",
                row.get("status"));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveDoctor(Long id, Requests.Doctor request) {
        // 科室先于医生行加锁，科室停用与医生变更不会发生锁反转。
        var previous = id == null ? null : store.require("doctor", id, false);
        long oldDepartment =
                previous == null ? request.departmentId() : number(previous.get("departmentId"));
        long low = Math.min(oldDepartment, request.departmentId());
        long high = Math.max(oldDepartment, request.departmentId());
        if (mapper.lockDepartmentIncludingDeleted(low) == null) {
            throw BusinessException.missing();
        }
        if (low != high) {
            if (mapper.lockDepartmentIncludingDeleted(high) == null) {
                throw BusinessException.missing();
            }
        }
        var department = store.require("department", request.departmentId(), false);
        if (request.status() == 1 && integer(department.get("status")) != 1) {
            throw BusinessException.bad("医生所属科室未启用");
        }
        if (id != null) {
            previous = store.require("doctor", id, true);
        }
        if (request.userId() != null) {
            var user = store.require("sys_user", request.userId(), false);
            if (integer(user.get("status")) != 1
                    || !mapper.roles(request.userId()).contains("DOCTOR")) {
                throw BusinessException.bad("关联账号必须为启用的医生账号");
            }
        }
        if (id != null
                && (request.status() == 0
                        || number(previous.get("departmentId")) != request.departmentId())) {
            cascadeDoctor(id, "医生停用或科室变更");
        }
        var values =
                fields(
                        "userId",
                        request.userId(),
                        "departmentId",
                        request.departmentId(),
                        "name",
                        request.name().trim(),
                        "title",
                        request.title(),
                        "specialty",
                        request.specialty(),
                        "introduction",
                        request.introduction(),
                        "avatarUrl",
                        request.avatarUrl(),
                        "status",
                        request.status());
        long targetId = id == null ? store.insert("doctor", values) : id;
        if (id != null) {
            store.update("doctor", id, values);
        }
        store.audit(id == null ? "DOCTOR_CREATE" : "DOCTOR_UPDATE", "doctor", targetId, "维护医生资料");
        return doctorView(store.require("doctor", targetId, false));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDoctor(long id) {
        var row = store.require("doctor", id, false);
        mapper.lockDepartmentIncludingDeleted(number(row.get("departmentId")));
        store.require("doctor", id, true);
        cascadeDoctor(id, "医生删除");
        store.softDelete("doctor", id);
        store.audit("DOCTOR_DELETE", "doctor", id, "逻辑删除医生并取消活动预约");
    }

    /** 调用方必须已锁定医生；按排班 ID 顺序取消，并始终先锁排班再锁预约。 */
    public void cascadeDoctor(long doctorId, String reason) {
        for (long scheduleId : mapper.doctorScheduleIds(doctorId)) {
            store.require("doctor_schedule", scheduleId, true);
            cancelScheduleAppointments(scheduleId, reason);
            store.update("doctor_schedule", scheduleId, fields("status", "CANCELLED"));
        }
    }

    /** 调用方已持有排班行锁。终态变更和号源释放处于同一个事务。 */
    public void cancelScheduleAppointments(long scheduleId, String reason) {
        for (long appointmentId : mapper.activeAppointmentIds(scheduleId)) {
            var appointment = store.require("appointment", appointmentId, true);
            if (!List.of("PENDING", "CONFIRMED").contains(appointment.get("status"))) {
                continue;
            }
            store.update(
                    "appointment",
                    appointmentId,
                    fields(
                            "status",
                            "CANCELLED",
                            "appointmentActiveKey",
                            null,
                            "cancelReason",
                            reason,
                            "cancelledAt",
                            now(),
                            "handledBy",
                            CurrentUser.get().id(),
                            "handledAt",
                            now()));
            if (mapper.release(scheduleId) != 1) {
                throw new IllegalStateException("Slot conservation violation");
            }
            store.audit("APPOINTMENT_CANCEL", "appointment", appointmentId, "管理操作取消活动预约");
        }
    }

    public PageResult<Map<String, Object>> notices(
            boolean publicOnly, String keyword, Integer status, int page, int size) {
        return mappedPage(
                store.page(
                        "clinic_notice", fields("status", status), keyword, publicOnly, page, size),
                this::noticeView);
    }

    private Map<String, Object> noticeView(Map<String, Object> row) {
        return view(
                fields(
                        "id",
                        row.get("id"),
                        "title",
                        row.get("title"),
                        "content",
                        row.get("content"),
                        "status",
                        row.get("status"),
                        "publishAt",
                        row.get("publishAt"),
                        "expireAt",
                        row.get("expireAt"),
                        "createdAt",
                        row.get("createdAt")));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveNotice(Long id, Requests.Notice request) {
        if (id != null) {
            store.require("clinic_notice", id, true);
        }
        if (request.expireAt() != null
                && request.publishAt() != null
                && !request.expireAt().isAfter(request.publishAt())) {
            throw BusinessException.bad("公告结束时间应晚于发布时间");
        }
        var values =
                fields(
                        "title",
                        request.title(),
                        "content",
                        request.content(),
                        "status",
                        request.status(),
                        "publishAt",
                        utc(request.publishAt()),
                        "expireAt",
                        utc(request.expireAt()));
        if (id == null) {
            values.put("createdBy", CurrentUser.get().id());
        }
        long targetId = id == null ? store.insert("clinic_notice", values) : id;
        if (id != null) {
            store.update("clinic_notice", id, values);
        }
        store.audit(
                id == null ? "NOTICE_CREATE" : "NOTICE_UPDATE",
                "clinic_notice",
                targetId,
                "维护诊所公告");
        return noticeView(store.require("clinic_notice", targetId, false));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteNotice(long id) {
        store.require("clinic_notice", id, true);
        store.softDelete("clinic_notice", id);
        store.audit("NOTICE_DELETE", "clinic_notice", id, "逻辑删除公告");
    }

    public static PageResult<Map<String, Object>> mappedPage(
            PageResult<Map<String, Object>> page,
            java.util.function.Function<Map<String, Object>, Map<String, Object>> transform) {
        return new PageResult<>(
                page.records().stream().map(transform).toList(),
                page.total(),
                page.page(),
                page.size());
    }
}
