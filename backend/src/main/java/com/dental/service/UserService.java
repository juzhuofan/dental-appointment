package com.dental.service;

import static com.dental.common.DataValues.*;

import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.UserRole;
import com.dental.persistence.BusinessMapper;
import com.dental.security.CurrentUser;
import com.dental.web.Requests;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserService {
    private final StoreService store;
    private final BusinessMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final CatalogService catalog;
    private final AppointmentService appointments;

    public UserService(
            StoreService store,
            BusinessMapper mapper,
            PasswordEncoder passwordEncoder,
            CatalogService catalog,
            AppointmentService appointments) {
        this.store = store;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.catalog = catalog;
        this.appointments = appointments;
    }

    public Map<String, Object> profile() {
        var row = store.findOne("patient_profile", fields("userId", CurrentUser.get().id()));
        if (row == null) {
            throw BusinessException.missing();
        }
        return profileView(row, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveProfile(Requests.Profile request) {
        long userId = CurrentUser.get().id();
        var row = store.findOne("patient_profile", fields("userId", userId));
        if (row == null) {
            mapper.upsertProfile(userId, request.realName());
            row = store.findOne("patient_profile", fields("userId", userId));
        }
        long id = number(row.get("id"));
        store.require("patient_profile", id, true);
        store.update(
                "patient_profile",
                id,
                fields(
                        "realName",
                        request.realName().trim(),
                        "phone",
                        request.phone(),
                        "gender",
                        request.gender() == null ? 0 : request.gender(),
                        "birthDate",
                        request.birthDate(),
                        "remark",
                        request.remark()));
        return profileView(store.require("patient_profile", id, false), false);
    }

    private Map<String, Object> profileView(Map<String, Object> row, boolean masked) {
        return view(
                fields(
                        "id",
                        row.get("id"),
                        "userId",
                        row.get("userId"),
                        "realName",
                        row.get("realName"),
                        "phone",
                        masked ? maskPhone((String) row.get("phone")) : row.get("phone"),
                        "gender",
                        row.get("gender"),
                        "birthDate",
                        row.get("birthDate"),
                        "remark",
                        row.get("remark")));
    }

    public PageResult<Map<String, Object>> patients(String keyword, int page, int size) {
        return CatalogService.mappedPage(
                store.page("patient_profile", fields(), keyword, false, page, size),
                row -> profileView(row, true));
    }

    public PageResult<Map<String, Object>> users(
            String keyword, String role, Integer status, int page, int size) {
        if (role != null && !UserRole.names().contains(role)) {
            throw BusinessException.bad("角色参数不正确");
        }
        return CatalogService.mappedPage(
                store.page(
                        "sys_user",
                        fields("role", role, "status", status),
                        keyword,
                        false,
                        page,
                        size),
                this::userView);
    }

    public Map<String, Object> userView(Map<String, Object> row) {
        var roles = mapper.roles(number(row.get("id")));
        return view(
                fields(
                        "id",
                        row.get("id"),
                        "username",
                        row.get("username"),
                        "displayName",
                        row.get("displayName"),
                        "status",
                        row.get("status"),
                        "role",
                        roles.isEmpty() ? null : roles.get(0),
                        "roles",
                        roles,
                        "createdAt",
                        row.get("createdAt"),
                        "updatedAt",
                        row.get("updatedAt")));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveUser(Long id, Requests.User request) {
        mapper.lockAdminRole();
        var previous = id == null ? null : store.require("sys_user", id, true);
        if (id == null && (request.password() == null || request.password().isBlank())) {
            throw BusinessException.bad("新增账号必须填写至少8位密码");
        }
        if (id != null) {
            if (previous.get("demoDeviceHash") != null
                    && (!"PATIENT".equals(request.role())
                            || !request.username().equals(previous.get("username"))
                            || request.password() != null)) {
                throw BusinessException.bad("演示患者账号不能更换角色、用户名或设置密码");
            }
            protectAdmin(id, request.status(), request.role());
            var oldRoles = mapper.roles(id);
            if (request.status() == 0 || !oldRoles.contains(request.role())) {
                deactivateAssociatedResources(id, oldRoles, request.role());
            }
        }
        var values =
                fields(
                        "username",
                        request.username(),
                        "displayName",
                        request.displayName(),
                        "status",
                        request.status());
        if (request.password() != null && !request.password().isBlank()) {
            values.put("passwordHash", passwordEncoder.encode(request.password()));
        }
        long targetId = id == null ? store.insert("sys_user", values) : id;
        if (id != null) {
            store.update("sys_user", id, values);
            mapper.unbindRoles(id);
            mapper.revokeUser(id);
        }
        mapper.bindRole(targetId, request.role());
        if ("PATIENT".equals(request.role())) {
            mapper.upsertProfile(targetId, request.displayName());
        }
        store.audit(id == null ? "USER_CREATE" : "USER_UPDATE", "sys_user", targetId, "维护登录账号及角色");
        return userView(store.require("sys_user", targetId, false));
    }

    private void protectAdmin(long id, int targetStatus, String targetRole) {
        if (id == CurrentUser.get().id() && (targetStatus != 1 || !"ADMIN".equals(targetRole))) {
            throw BusinessException.bad("不能停用自己或移除自己的管理员角色");
        }
        var old = store.require("sys_user", id, false);
        if (integer(old.get("status")) == 1
                && mapper.roles(id).contains("ADMIN")
                && (targetStatus != 1 || !"ADMIN".equals(targetRole))
                && mapper.activeAdminCount() <= 1) {
            throw BusinessException.conflict("LAST_ADMIN_PROTECTED", "不能停用或删除最后一个启用管理员");
        }
    }

    private void deactivateAssociatedResources(long id, List<String> oldRoles, String targetRole) {
        if (oldRoles.contains("DOCTOR")) {
            var doctor = store.findOne("doctor", fields("userId", id));
            if (doctor != null) {
                long doctorId = number(doctor.get("id"));
                mapper.lockDepartmentIncludingDeleted(number(doctor.get("departmentId")));
                store.require("doctor", doctorId, true);
                catalog.cascadeDoctor(doctorId, "医生账号停用、删除或角色变更");
                var changes = fields("status", 0);
                if (!"DOCTOR".equals(targetRole)) {
                    changes.put("userId", null);
                }
                store.update("doctor", doctorId, changes);
            }
        }
        if (oldRoles.contains("PATIENT")) {
            appointments.cancelPatient(id);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(long id) {
        mapper.lockAdminRole();
        store.require("sys_user", id, true);
        protectAdmin(id, 0, "");
        deactivateAssociatedResources(id, mapper.roles(id), "");
        var profile = store.findOne("patient_profile", fields("userId", id));
        if (profile != null) {
            store.softDelete("patient_profile", number(profile.get("id")));
        }
        mapper.unbindRoles(id);
        mapper.revokeUser(id);
        store.softDelete("sys_user", id);
        store.audit("USER_DELETE", "sys_user", id, "逻辑删除账号并撤销登录凭证");
    }

    public PageResult<Map<String, Object>> operationLogs(
            String action, String keyword, int page, int size) {
        return CatalogService.mappedPage(
                store.page("operation_log", fields("action", action), keyword, false, page, size),
                row ->
                        view(
                                fields(
                                        "id",
                                        row.get("id"),
                                        "operatorUserId",
                                        row.get("operatorUserId"),
                                        "operatorName",
                                        row.get("operatorName"),
                                        "operatorRole",
                                        row.get("operatorRole"),
                                        "action",
                                        row.get("action"),
                                        "targetType",
                                        row.get("targetType"),
                                        "targetId",
                                        row.get("targetId"),
                                        "summary",
                                        row.get("summary"),
                                        "createdAt",
                                        row.get("createdAt"))));
    }
}
