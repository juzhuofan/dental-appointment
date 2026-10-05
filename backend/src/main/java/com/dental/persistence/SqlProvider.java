package com.dental.persistence;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** SQL 中只有服务端白名单标识符；所有数据均通过 MyBatis 参数绑定。 */
public final class SqlProvider {
    private static final Map<String, List<String>> TABLES = new LinkedHashMap<>();
    private static final List<String> COMMON = List.of("id", "deleted", "createdAt", "updatedAt");

    static {
        define(
                "sys_user",
                "username,passwordHash,phone,wechatOpenid,demoDeviceHash,displayName,avatarUrl,status,lastLoginAt");
        define("sys_role", "roleCode,roleName,description");
        define("sys_user_role", "userId,roleId");
        define("patient_profile", "userId,realName,phone,gender,birthDate,remark");
        define("department", "name,description,sortOrder,status");
        define("doctor", "userId,departmentId,name,title,specialty,introduction,avatarUrl,status");
        define(
                "doctor_schedule",
                "doctorId,departmentId,startTime,endTime,totalSlots,bookedSlots,status,cancelBeforeMinutes,version,createdBy");
        define(
                "appointment",
                "appointmentNo,patientUserId,patientProfileId,scheduleId,doctorId,departmentId,patientNameSnapshot,patientPhoneSnapshot,doctorNameSnapshot,departmentNameSnapshot,startTimeSnapshot,endTimeSnapshot,chiefComplaint,status,appointmentActiveKey,cancelReason,cancelledAt,handledBy,handledAt");
        define("clinic_notice", "title,content,status,publishAt,expireAt,createdBy");
        define(
                "operation_log",
                "operatorUserId,operatorName,operatorRole,action,targetType,targetId,summary,ipAddress");
        define("system_config", "configKey,configValue,description,updatedBy");
        define("auth_token", "tokenId,userId,expiresAt,revoked");
        define("appointment_idempotency", "userId,requestKey,requestHash,appointmentId");
    }

    private static void define(String table, String fields) {
        var columns = new java.util.ArrayList<>(COMMON);
        columns.addAll(Arrays.asList(fields.split(",")));
        TABLES.put(table, List.copyOf(columns));
    }

    private static String table(Map<String, Object> parameters) {
        String table = (String) parameters.get("table");
        if (!TABLES.containsKey(table)) {
            throw new IllegalArgumentException("Unknown table");
        }
        return table;
    }

    public static String column(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    public static String find(Map<String, Object> parameters) {
        String table = table(parameters);
        return "SELECT "
                + columns(table)
                + " FROM "
                + table
                + " WHERE id=#{id} AND deleted=0"
                + (Boolean.TRUE.equals(parameters.get("lock")) ? " FOR UPDATE" : "");
    }

    private static String columns(String table) {
        return TABLES.get(table).stream()
                .map(key -> column(key) + " AS " + key)
                .collect(Collectors.joining(","));
    }

    public static String list(Map<String, Object> parameters) {
        String table = table(parameters);
        String order =
                switch (table) {
                    case "department" -> "sort_order ASC,id ASC";
                    case "doctor_schedule" -> "start_time ASC,id ASC";
                    default -> "id DESC";
                };
        return "SELECT "
                + columns(table)
                + " FROM "
                + table
                + where(parameters, table)
                + " ORDER BY "
                + order
                + " LIMIT #{size} OFFSET #{offset}";
    }

    public static String count(Map<String, Object> parameters) {
        String table = table(parameters);
        return "SELECT COUNT(*) FROM " + table + where(parameters, table);
    }

    @SuppressWarnings("unchecked")
    private static String where(Map<String, Object> parameters, String table) {
        StringBuilder sql = new StringBuilder(" WHERE deleted=0");
        Map<String, Object> filters = (Map<String, Object>) parameters.get("filters");
        for (String key : filters.keySet()) {
            if (filters.get(key) == null) {
                continue;
            }
            if ("sys_user".equals(table) && "role".equals(key)) {
                sql.append(
                        " AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON"
                            + " r.id=ur.role_id WHERE ur.user_id=sys_user.id AND ur.deleted=0 AND"
                            + " r.deleted=0 AND r.role_code=#{filters.role})");
                continue;
            }
            String operation = "=";
            String field = key;
            for (String suffix : List.of("Ge", "Gt", "Lt", "Le")) {
                if (key.endsWith(suffix)) {
                    field = key.substring(0, key.length() - 2);
                    operation = Map.of("Ge", ">=", "Gt", ">", "Lt", "<", "Le", "<=").get(suffix);
                    break;
                }
            }
            if (!TABLES.get(table).contains(field)) {
                throw new IllegalArgumentException("Unknown filter");
            }
            sql.append(" AND ")
                    .append(column(field))
                    .append(operation)
                    .append("#{filters.")
                    .append(key)
                    .append('}');
        }
        Object keyword = parameters.get("keyword");
        if (keyword != null && !keyword.toString().isBlank()) {
            List<String> names =
                    switch (table) {
                        case "sys_user" -> List.of("username", "display_name");
                        case "patient_profile" -> List.of("real_name", "phone");
                        case "appointment" ->
                                List.of(
                                        "appointment_no",
                                        "patient_name_snapshot",
                                        "doctor_name_snapshot");
                        case "operation_log" -> List.of("operator_name", "summary", "action");
                        case "clinic_notice" -> List.of("title");
                        case "doctor" -> List.of("name", "specialty");
                        case "department" -> List.of("name");
                        default -> List.of();
                    };
            if (!names.isEmpty()) {
                sql.append(" AND (")
                        .append(
                                names.stream()
                                        .map(name -> name + " LIKE CONCAT('%',#{keyword},'%')")
                                        .collect(Collectors.joining(" OR ")))
                        .append(')');
            }
        }
        if (Boolean.TRUE.equals(parameters.get("publicOnly"))) {
            switch (table) {
                case "doctor" ->
                        sql.append(
                                " AND status=1 AND EXISTS (SELECT 1 FROM department dep WHERE"
                                        + " dep.id=doctor.department_id AND dep.status=1 AND"
                                        + " dep.deleted=0)");
                case "doctor_schedule" ->
                        sql.append(
                                " AND status='PUBLISHED' AND start_time>UTC_TIMESTAMP(3) AND EXISTS"
                                        + " (SELECT 1 FROM doctor doc JOIN department dep ON"
                                        + " dep.id=doc.department_id WHERE"
                                        + " doc.id=doctor_schedule.doctor_id AND doc.status=1 AND"
                                        + " doc.deleted=0 AND dep.status=1 AND dep.deleted=0)");
                case "clinic_notice" ->
                        sql.append(
                                " AND status=1 AND (publish_at IS NULL OR"
                                        + " publish_at<=UTC_TIMESTAMP(3)) AND (expire_at IS NULL OR"
                                        + " expire_at>UTC_TIMESTAMP(3))");
                case "department" -> sql.append(" AND status=1");
                default -> throw new IllegalArgumentException("Public query not supported");
            }
        }
        return sql.toString();
    }

    @SuppressWarnings("unchecked")
    public static String insert(Map<String, Object> parameters) {
        String table = table(parameters);
        Map<String, Object> values = (Map<String, Object>) parameters.get("values");
        checkValues(table, values.keySet());
        return "INSERT INTO "
                + table
                + " ("
                + values.keySet().stream().map(SqlProvider::column).collect(Collectors.joining(","))
                + ") VALUES ("
                + values.keySet().stream()
                        .map(key -> "#{values." + key + "}")
                        .collect(Collectors.joining(","))
                + ")";
    }

    @SuppressWarnings("unchecked")
    public static String update(Map<String, Object> parameters) {
        String table = table(parameters);
        Map<String, Object> values = (Map<String, Object>) parameters.get("values");
        checkValues(table, values.keySet());
        return "UPDATE "
                + table
                + " SET "
                + values.keySet().stream()
                        .map(key -> column(key) + "=#{values." + key + "}")
                        .collect(Collectors.joining(","))
                + " WHERE id=#{id} AND deleted=0";
    }

    private static void checkValues(String table, Set<String> values) {
        if (values.contains("id") || !TABLES.get(table).containsAll(values)) {
            throw new IllegalArgumentException("Unknown field");
        }
    }
}
