package com.dental.persistence;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface BusinessMapper {
    /** 仅供维护历史关联的锁定查询，普通读取仍始终过滤 deleted=0。 */
    @Select("SELECT id,deleted FROM department WHERE id=#{id} FOR UPDATE")
    Map<String, Object> lockDepartmentIncludingDeleted(long id);

    @Select(
            "SELECT COUNT(DISTINCT patient_user_id) FROM appointment WHERE doctor_id=#{doctorId}"
                    + " AND deleted=0")
    long doctorPatientCount(long doctorId);

    @Select(
            "SELECT DISTINCT schedule_id FROM appointment WHERE patient_user_id=#{userId} AND"
                    + " deleted=0 AND status IN ('PENDING','CONFIRMED') ORDER BY schedule_id")
    List<Long> patientActiveScheduleIds(long userId);

    @Select(
            "SELECT id FROM appointment WHERE patient_user_id=#{userId} AND"
                + " schedule_id=#{scheduleId} AND deleted=0 AND status IN ('PENDING','CONFIRMED')"
                + " ORDER BY id")
    List<Long> activePatientAppointmentIds(
            @Param("scheduleId") long scheduleId, @Param("userId") long userId);

    @Insert(
            "INSERT INTO"
                + " sys_user(username,password_hash,demo_device_hash,display_name,status,created_at,updated_at)"
                + " VALUES(#{username},#{passwordHash},#{hash},'演示患者',1,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))"
                + " ON DUPLICATE KEY UPDATE updated_at=UTC_TIMESTAMP(3)")
    int upsertDemo(
            @Param("hash") String hash,
            @Param("username") String username,
            @Param("passwordHash") String passwordHash);

    @Insert(
            "INSERT INTO patient_profile(user_id,real_name,phone,gender,created_at,updated_at)"
                + " VALUES(#{userId},#{name},'13800000000',0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)) ON"
                + " DUPLICATE KEY UPDATE deleted=0,updated_at=UTC_TIMESTAMP(3)")
    int upsertProfile(@Param("userId") long userId, @Param("name") String name);

    @SelectProvider(type = SqlProvider.class, method = "find")
    Map<String, Object> find(
            @Param("table") String table, @Param("id") long id, @Param("lock") boolean lock);

    @SelectProvider(type = SqlProvider.class, method = "list")
    List<Map<String, Object>> list(
            @Param("table") String table,
            @Param("filters") Map<String, Object> filters,
            @Param("keyword") String keyword,
            @Param("publicOnly") boolean publicOnly,
            @Param("offset") long offset,
            @Param("size") int size);

    @SelectProvider(type = SqlProvider.class, method = "count")
    long count(
            @Param("table") String table,
            @Param("filters") Map<String, Object> filters,
            @Param("keyword") String keyword,
            @Param("publicOnly") boolean publicOnly);

    @InsertProvider(type = SqlProvider.class, method = "insert")
    @Options(useGeneratedKeys = true, keyProperty = "values.id", keyColumn = "id")
    int insert(@Param("table") String table, @Param("values") Map<String, Object> values);

    @UpdateProvider(type = SqlProvider.class, method = "update")
    int update(
            @Param("table") String table,
            @Param("id") long id,
            @Param("values") Map<String, Object> values);

    @Select(
            "SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE"
                    + " ur.user_id=#{userId} AND ur.deleted=0 AND r.deleted=0")
    List<String> roles(long userId);

    @Select("SELECT id FROM doctor WHERE user_id=#{userId} AND deleted=0 AND status=1 LIMIT 1")
    Long doctorId(long userId);

    @Insert(
            "INSERT INTO sys_user_role(user_id,role_id,deleted,created_at,updated_at) SELECT"
                    + " #{userId},id,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3) FROM sys_role WHERE"
                    + " role_code=#{role} AND deleted=0 ON DUPLICATE KEY UPDATE"
                    + " deleted=0,updated_at=UTC_TIMESTAMP(3)")
    int bindRole(@Param("userId") long userId, @Param("role") String role);

    @Update(
            "UPDATE sys_user_role SET deleted=1,updated_at=UTC_TIMESTAMP(3) WHERE user_id=#{userId}"
                    + " AND deleted=0")
    int unbindRoles(long userId);

    @Update(
            "UPDATE auth_token SET revoked=1,updated_at=UTC_TIMESTAMP(3) WHERE user_id=#{userId}"
                    + " AND deleted=0 AND revoked=0")
    int revokeUser(long userId);

    @Update(
            "UPDATE auth_token SET revoked=1,updated_at=UTC_TIMESTAMP(3) WHERE token_id=#{tokenId}"
                    + " AND deleted=0")
    int revokeToken(String tokenId);

    @Select(
            "SELECT COUNT(*) FROM doctor_schedule WHERE doctor_id=#{doctorId} AND deleted=0 AND"
                    + " status<>'CANCELLED' AND id<>#{excludeId} AND start_time<#{end} AND"
                    + " end_time>#{start}")
    int overlaps(
            @Param("doctorId") long doctorId,
            @Param("excludeId") long excludeId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Select(
            "SELECT id FROM doctor_schedule WHERE doctor_id=#{doctorId} AND deleted=0 AND"
                    + " status<>'CANCELLED' ORDER BY id")
    List<Long> doctorScheduleIds(long doctorId);

    @Select("SELECT id FROM doctor WHERE department_id=#{departmentId} AND deleted=0 ORDER BY id")
    List<Long> departmentDoctorIds(long departmentId);

    @Select(
            "SELECT id FROM appointment WHERE schedule_id=#{scheduleId} AND status IN"
                    + " ('PENDING','CONFIRMED') AND deleted=0 ORDER BY id")
    List<Long> activeAppointmentIds(long scheduleId);

    @Update(
            "UPDATE doctor_schedule SET"
                + " booked_slots=booked_slots+1,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE"
                + " id=#{id} AND deleted=0 AND status='PUBLISHED' AND start_time>UTC_TIMESTAMP(3)"
                + " AND booked_slots<total_slots")
    int occupy(long id);

    @Update(
            "UPDATE doctor_schedule SET"
                + " booked_slots=booked_slots-1,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE"
                + " id=#{id} AND deleted=0 AND booked_slots>0")
    int release(long id);

    @Select("SELECT id FROM sys_role WHERE role_code='ADMIN' AND deleted=0 FOR UPDATE")
    long lockAdminRole();

    @Select(
            "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role"
                    + " r ON r.id=ur.role_id WHERE u.status=1 AND u.deleted=0 AND ur.deleted=0 AND"
                    + " r.role_code='ADMIN' AND r.deleted=0")
    long activeAdminCount();

    @Insert(
            "INSERT INTO"
                + " appointment_idempotency(user_id,request_key,request_hash,created_at,updated_at)"
                + " VALUES(#{userId},#{requestKey},#{requestHash},UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))"
                + " ON DUPLICATE KEY UPDATE updated_at=updated_at")
    int reserveIdempotency(
            @Param("userId") long userId,
            @Param("requestKey") String requestKey,
            @Param("requestHash") String requestHash);

    @Select(
            "SELECT id,request_hash AS requestHash,appointment_id AS appointmentId FROM"
                + " appointment_idempotency WHERE user_id=#{userId} AND request_key=#{requestKey}"
                + " AND deleted=0 FOR UPDATE")
    Map<String, Object> lockIdempotency(
            @Param("userId") long userId, @Param("requestKey") String requestKey);

    @Select(
            "<script>SELECT status,COUNT(*) AS count FROM appointment WHERE deleted=0 <if"
                    + " test='doctorId != null'>AND doctor_id=#{doctorId}</if> GROUP BY"
                    + " status</script>")
    List<Map<String, Object>> statusCounts(@Param("doctorId") Long doctorId);

    @Select(
            "<script>SELECT DATE(DATE_ADD(created_at,INTERVAL 8 HOUR)) AS date,COUNT(*) AS count"
                + " FROM appointment WHERE deleted=0 AND created_at>=#{start} <if test='doctorId !="
                + " null'>AND doctor_id=#{doctorId}</if> GROUP BY DATE(DATE_ADD(created_at,INTERVAL"
                + " 8 HOUR)) ORDER BY date</script>")
    List<Map<String, Object>> dailyTrend(
            @Param("doctorId") Long doctorId, @Param("start") LocalDateTime start);
}
