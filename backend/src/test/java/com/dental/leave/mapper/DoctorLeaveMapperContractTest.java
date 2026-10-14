package com.dental.leave.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 解析真实映射，检查生成器/审批依赖的锁定和时间边界契约；不连接数据库。 */
class DoctorLeaveMapperContractTest {
    private Configuration configuration;
    private static final String NS = "com.dental.leave.mapper.DoctorLeaveMapper.";

    @BeforeEach
    void parseMapper() throws Exception {
        configuration = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/mapper/DoctorLeaveMapper.xml")) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, "mapper/DoctorLeaveMapper.xml", configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void approvedOverlapIsHalfOpenCurrentReadAndExcludesPendingAndDeleted() {
        String sql = sql("countApprovedOverlaps", interval());
        assertTrue(sql.contains("status = 'APPROVED'"));
        assertTrue(sql.contains("deleted = 0"));
        assertTrue(sql.contains("start_time < ? AND end_time > ?"));
        assertTrue(sql.endsWith("FOR UPDATE"));
    }

    @Test
    void approvalsLockDraftPublishedAndClosedInStableOrder() {
        String sql = sql("lockOverlappingScheduleIds", interval());
        assertTrue(sql.contains("status IN ('DRAFT', 'PUBLISHED', 'CLOSED')"));
        assertTrue(sql.contains("start_time < ? AND end_time > ?"));
        assertTrue(sql.endsWith("ORDER BY id FOR UPDATE"));
    }

    @Test
    void confirmationCountsOnlyActiveAppointmentsWithCurrentRead() {
        String sql = sql("countActiveAppointments", Map.of("scheduleIds", List.of(101L, 102L)));
        assertTrue(sql.contains("status IN ('PENDING', 'CONFIRMED')"));
        assertTrue(sql.contains("deleted = 0"));
        assertTrue(sql.endsWith("FOR UPDATE"));
        assertEquals(2, configuration.getMappedStatement(NS + "countActiveAppointments")
                .getBoundSql(Map.of("scheduleIds", List.of(101L, 102L))).getParameterMappings().size());
    }

    @Test
    void transitionsRequirePendingAndCancellationOriginIsIdempotent() {
        assertTrue(sql("reviewPending", Map.of()).contains("AND status = 'PENDING'"));
        assertTrue(sql("withdrawPending", Map.of()).contains("doctor_id = ? AND deleted = 0 AND status = 'PENDING'"));
        assertTrue(sql("linkCancelledSchedule", Map.of()).contains("INSERT IGNORE INTO doctor_leave_schedule"));
    }

    private String sql(String id, Map<String, ?> arguments) {
        return configuration.getMappedStatement(NS + id).getBoundSql(arguments).getSql().replaceAll("\\s+", " ").trim();
    }

    private static Map<String, ?> interval() {
        return Map.of("doctorId", 10L, "start", LocalDateTime.of(2099, 5, 4, 1, 0),
                "end", LocalDateTime.of(2099, 5, 4, 5, 0));
    }
}
