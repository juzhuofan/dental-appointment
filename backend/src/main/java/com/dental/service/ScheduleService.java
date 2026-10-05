package com.dental.service;

import static com.dental.common.DataValues.*;

import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.persistence.BusinessMapper;
import com.dental.security.CurrentUser;
import com.dental.web.Requests;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class ScheduleService {
    private final StoreService store;
    private final BusinessMapper mapper;
    private final CatalogService catalog;

    public ScheduleService(StoreService store, BusinessMapper mapper, CatalogService catalog) {
        this.store = store;
        this.mapper = mapper;
        this.catalog = catalog;
    }

    public PageResult<Map<String, Object>> list(
            boolean publicOnly,
            Long doctorId,
            Long departmentId,
            String from,
            String to,
            String status,
            int page,
            int size) {
        var filter = fields("doctorId", doctorId, "departmentId", departmentId, "status", status);
        dateFilters(filter, "startTime", from, to);
        if (!publicOnly && !CurrentUser.get().isAdmin()) {
            filter.put("doctorId", requireDoctorId());
        }
        return CatalogService.mappedPage(
                store.page("doctor_schedule", filter, null, publicOnly, page, size),
                this::viewSchedule);
    }

    public Long requireDoctorId() {
        var user = CurrentUser.get();
        if (!user.isDoctor() || user.doctorId() == null) {
            throw BusinessException.forbidden();
        }
        return user.doctorId();
    }

    public Map<String, Object> detail(long id) {
        var row = store.require("doctor_schedule", id, false);
        var doctor = mapper.find("doctor", number(row.get("doctorId")), false);
        var department = mapper.find("department", number(row.get("departmentId")), false);
        if (!"PUBLISHED".equals(row.get("status"))
                || !time(row.get("startTime")).isAfter(now())
                || doctor == null
                || department == null
                || integer(doctor.get("status")) != 1
                || integer(department.get("status")) != 1) {
            throw BusinessException.missing();
        }
        return viewSchedule(row);
    }

    public Map<String, Object> viewSchedule(Map<String, Object> row) {
        var doctor = mapper.find("doctor", number(row.get("doctorId")), false);
        var department = mapper.find("department", number(row.get("departmentId")), false);
        return view(
                fields(
                        "id",
                        row.get("id"),
                        "doctorId",
                        row.get("doctorId"),
                        "doctorName",
                        doctor == null ? "已删除医生" : doctor.get("name"),
                        "departmentId",
                        row.get("departmentId"),
                        "departmentName",
                        department == null ? "已删除科室" : department.get("name"),
                        "startTime",
                        row.get("startTime"),
                        "endTime",
                        row.get("endTime"),
                        "totalSlots",
                        row.get("totalSlots"),
                        "bookedSlots",
                        row.get("bookedSlots"),
                        "remainingSlots",
                        integer(row.get("totalSlots")) - integer(row.get("bookedSlots")),
                        "status",
                        row.get("status"),
                        "cancelBeforeMinutes",
                        row.get("cancelBeforeMinutes")));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(Long id, Requests.Schedule request) {
        LocalDateTime start = utc(request.startTime());
        LocalDateTime end = utc(request.endTime());
        if (!end.isAfter(start)) {
            throw BusinessException.bad("结束时间必须晚于开始时间");
        }
        if (id == null && !start.isAfter(now())) {
            throw BusinessException.bad("新排班必须选择未来时间");
        }
        var incomingDoctor = store.require("doctor", request.doctorId(), false);
        var previous = id == null ? null : store.require("doctor_schedule", id, false);
        if (previous != null && number(previous.get("doctorId")) != request.doctorId()) {
            throw BusinessException.conflict("SCHEDULE_DOCTOR_IMMUTABLE", "排班所属医生不可更换，请创建新的排班");
        }
        long departmentId = number(incomingDoctor.get("departmentId"));
        var department = store.require("department", departmentId, true);
        var doctor = store.require("doctor", request.doctorId(), true);
        if (number(doctor.get("departmentId")) != departmentId) {
            throw BusinessException.conflict("DOCTOR_CHANGED", "医生科室已变更，请刷新后重试");
        }
        if ("PUBLISHED".equals(request.status())
                && (integer(doctor.get("status")) != 1 || integer(department.get("status")) != 1)) {
            throw BusinessException.bad("医生及科室启用后才能发布排班");
        }
        if (id != null) {
            previous = store.require("doctor_schedule", id, true);
            int booked = integer(previous.get("bookedSlots"));
            if (booked > 0
                    && (!time(previous.get("startTime")).equals(start)
                            || !time(previous.get("endTime")).equals(end))) {
                throw BusinessException.conflict("SCHEDULE_HAS_APPOINTMENTS", "已有预约的排班不能修改时间");
            }
            if (request.totalSlots() < booked) {
                throw BusinessException.conflict("SCHEDULE_CAPACITY_TOO_SMALL", "容量不能小于已占用号源");
            }
        }
        if (!"CANCELLED".equals(request.status())
                && mapper.overlaps(request.doctorId(), id == null ? 0 : id, start, end) > 0) {
            throw BusinessException.conflict("SCHEDULE_OVERLAP", "医生在该时段已有排班");
        }
        var values =
                fields(
                        "doctorId",
                        request.doctorId(),
                        "departmentId",
                        departmentId,
                        "startTime",
                        start,
                        "endTime",
                        end,
                        "totalSlots",
                        request.totalSlots(),
                        "status",
                        request.status(),
                        "cancelBeforeMinutes",
                        request.cancelBeforeMinutes() == null
                                ? catalog.cancelBeforeMinutes()
                                : request.cancelBeforeMinutes());
        if (id == null) {
            values.put("bookedSlots", 0);
            values.put("createdBy", CurrentUser.get().id());
        }
        long targetId = id == null ? store.insert("doctor_schedule", values) : id;
        if (id != null) {
            if (!"PUBLISHED".equals(request.status())) {
                catalog.cancelScheduleAppointments(id, "排班关闭或停诊");
            }
            store.update("doctor_schedule", id, values);
        }
        store.audit(
                id == null ? "SCHEDULE_CREATE" : "SCHEDULE_UPDATE",
                "doctor_schedule",
                targetId,
                "维护医生排班及容量");
        return viewSchedule(store.require("doctor_schedule", targetId, false));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> status(long id, Requests.ScheduleStatus request) {
        var prior = store.require("doctor_schedule", id, false);
        var doctor = store.require("doctor", number(prior.get("doctorId")), false);
        var department = store.require("department", number(doctor.get("departmentId")), true);
        doctor = store.require("doctor", number(prior.get("doctorId")), true);
        var schedule = store.require("doctor_schedule", id, true);
        if ("PUBLISHED".equals(request.status())) {
            if (integer(doctor.get("status")) != 1
                    || integer(department.get("status")) != 1
                    || !time(schedule.get("startTime")).isAfter(now())) {
                throw BusinessException.bad("排班已过期或医生科室未启用");
            }
            if (mapper.overlaps(
                            number(schedule.get("doctorId")),
                            id,
                            time(schedule.get("startTime")),
                            time(schedule.get("endTime")))
                    > 0) {
                throw BusinessException.conflict("SCHEDULE_OVERLAP", "医生在该时段已有排班");
            }
        } else {
            catalog.cancelScheduleAppointments(id, "排班关闭或停诊");
        }
        store.update(
                "doctor_schedule",
                id,
                fields("status", request.status(), "departmentId", doctor.get("departmentId")));
        store.audit("SCHEDULE_STATUS", "doctor_schedule", id, "更新排班发布状态");
        return viewSchedule(store.require("doctor_schedule", id, false));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        var schedule = store.require("doctor_schedule", id, true);
        catalog.cancelScheduleAppointments(id, "排班删除");
        store.softDelete("doctor_schedule", id);
        store.audit("SCHEDULE_DELETE", "doctor_schedule", id, "逻辑删除排班并取消活动预约");
    }
}
