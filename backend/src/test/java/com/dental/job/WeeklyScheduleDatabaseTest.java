package com.dental.job;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.dental.appointment.service.AppointmentService;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.common.TimeUtil;
import com.dental.config.AutomaticFieldFill;
import com.dental.department.service.ClinicService;
import com.dental.job.service.DoctorWeekGenerator;
import com.dental.job.service.WeeklyScheduleDates;
import com.dental.job.service.WeeklyScheduleService;
import com.dental.leave.dto.DoctorLeaveApplyDTO;
import com.dental.leave.dto.DoctorLeaveReviewDTO;
import com.dental.leave.mapper.DoctorLeaveMapper;
import com.dental.leave.service.DoctorLeaveService;
import com.dental.schedule.dto.ScheduleSaveDTO;
import com.dental.schedule.mapper.ScheduleMapper;
import com.dental.schedule.service.ScheduleService;
import com.dental.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 显式启用的 MySQL 8 业务/锁定集成测试。
 * 只允许固定的回环 13316 独立 dental_weekly_test 库，不读取 Boot 配置或业务库凭据。
 * 运行前由调用方在临时 datadir 启动该实例并创建空库，再传 -Dweekly.database=true。
 */
@EnabledIfSystemProperty(named = "weekly.database", matches = "true")
class WeeklyScheduleDatabaseTest {
    private static final String DATABASE = "dental_weekly_test";
    private static final String JDBC_URL = "jdbc:mysql://127.0.0.1:13316/" + DATABASE
            + "?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
            + "&connectTimeout=5000&socketTimeout=15000&sessionVariables=innodb_lock_wait_timeout=10";
    private static final long DOCTOR = 10L;
    private static final long DEPARTMENT = 20L;
    private static AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private ScheduleService schedules;
    private DoctorLeaveService leaves;
    private WeeklyScheduleService weekly;
    private DoctorWeekGenerator generator;
    private LocalDate targetMonday;
    private LocalDateTime targetStart;
    private LocalDateTime targetEnd;

    @BeforeAll
    static void openIsolatedContext() {
        context = new AnnotationConfigApplicationContext(DatabaseConfiguration.class);
    }

    @AfterAll
    static void closeIsolatedContext() {
        if (context != null) { context.close(); }
    }

    @BeforeEach
    void resetDedicatedTestDatabase() {
        jdbc = context.getBean(JdbcTemplate.class);
        schedules = context.getBean(ScheduleService.class);
        leaves = context.getBean(DoctorLeaveService.class);
        weekly = context.getBean(WeeklyScheduleService.class);
        generator = context.getBean(DoctorWeekGenerator.class);
        assertEquals(DATABASE, jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals(13316, jdbc.queryForObject("SELECT @@port", Integer.class));
        // 固定表名且固定专用数据库；不做可配置的清库、跨库删除或动态表名操作。
        jdbc.update("DELETE FROM doctor_leave_schedule");
        jdbc.update("DELETE FROM schedule_generation_item");
        jdbc.update("DELETE FROM appointment_idempotency");
        jdbc.update("DELETE FROM appointment");
        jdbc.update("DELETE FROM doctor_leave");
        jdbc.update("DELETE FROM doctor_schedule");
        jdbc.update("DELETE FROM doctor");
        jdbc.update("DELETE FROM department");
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("INSERT INTO department(id,name,status,deleted,created_at,updated_at) "
                + "VALUES (20,'独立测试口腔科',1,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))");
        jdbc.update("INSERT INTO doctor(id,department_id,name,status,deleted,created_at,updated_at) "
                + "VALUES (10,20,'独立测试医生',1,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))");
        targetMonday = WeeklyScheduleDates.mondayOf(TimeUtil.clinicToday()).plusWeeks(2);
        targetStart = atClinic(targetMonday.plusDays(1), 9, 0);
        targetEnd = targetStart.plusHours(1);
        seedSchedule(targetStart.minusWeeks(1), targetEnd.minusWeeks(1), "PUBLISHED", 7, 2, 90);
        authenticateAdmin();
    }

    @AfterEach
    void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    void generatesPublishedCapacityAndCutoffThenRetriesWithoutResurrectingDeletedShift() {
        var first = weekly.generate(targetMonday);
        assertTrue(first.successful());
        assertEquals(1, first.created());
        long generatedId = scheduleId(targetStart);
        assertEquals("PUBLISHED", scheduleStatus(generatedId));
        assertEquals(7, number("SELECT total_slots FROM doctor_schedule WHERE id=?", generatedId));
        assertEquals(0, number("SELECT booked_slots FROM doctor_schedule WHERE id=?", generatedId));
        assertEquals(90, number("SELECT cancel_before_minutes FROM doctor_schedule WHERE id=?", generatedId));
        assertEquals(targetEnd, timestamp("SELECT end_time FROM doctor_schedule WHERE id=?", generatedId));
        assertEquals("CREATED", ledgerOutcome(targetStart));

        assertEquals(0, weekly.generate(targetMonday).created());
        assertEquals(1, number("SELECT COUNT(*) FROM schedule_generation_item"));
        schedules.delete(generatedId);
        assertEquals(1, number("SELECT deleted FROM doctor_schedule WHERE id=?", generatedId));

        var retry = weekly.generate(targetMonday);
        assertEquals(0, retry.created());
        assertEquals(1, retry.existing());
        assertEquals(0, liveSchedules(targetStart));
        assertEquals(1, number("SELECT COUNT(*) FROM schedule_generation_item"));
    }

    @Test
    void approvedLeaveProducesLedgerSeedAndFollowingWeekResumesNominalShift() {
        long leaveId = applyLeave(targetStart, targetEnd);
        leaves.review(leaveId, approve(false));

        var onLeave = weekly.generate(targetMonday);
        assertTrue(onLeave.successful());
        assertEquals(0, onLeave.created());
        assertEquals(1, onLeave.leave());
        assertEquals("LEAVE", ledgerOutcome(targetStart));
        assertEquals(0, liveSchedules(targetStart));
        assertEquals(0, weekly.generate(targetMonday).created());

        var resumed = weekly.generate(targetMonday.plusWeeks(1));
        assertTrue(resumed.successful());
        assertEquals(1, resumed.created());
        assertEquals("PUBLISHED", scheduleStatus(scheduleId(targetStart.plusWeeks(1))));
        assertEquals(7, number("SELECT total_slots FROM doctor_schedule WHERE start_time=? AND deleted=0",
                sqlTime(targetStart.plusWeeks(1))));
        assertEquals(90, number("SELECT cancel_before_minutes FROM doctor_schedule WHERE start_time=? AND deleted=0",
                sqlTime(targetStart.plusWeeks(1))));
    }

    @Test
    void approvalAfterGenerationRequiresConfirmationAndAtomicallyCancelsBookingAndReleasesSlot() {
        assertEquals(1, weekly.generate(targetMonday).created());
        long generatedId = scheduleId(targetStart);
        seedActiveAppointment(generatedId);
        long leaveId = applyLeave(targetStart, targetEnd);

        var confirmation = assertThrows(BusinessException.class, () -> leaves.review(leaveId, approve(false)));
        assertEquals(409, confirmation.getHttpStatus());
        assertEquals("LEAVE_APPOINTMENT_CONFIRMATION_REQUIRED", confirmation.getCode());
        assertEquals("PENDING", leaveStatus(leaveId));
        assertEquals("PUBLISHED", scheduleStatus(generatedId));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM appointment WHERE schedule_id=?", String.class, generatedId));
        assertEquals(1, number("SELECT booked_slots FROM doctor_schedule WHERE id=?", generatedId));
        assertEquals(0, number("SELECT COUNT(*) FROM doctor_leave_schedule"));
        assertEquals(0, number("SELECT COUNT(*) FROM doctor_leave WHERE id=? AND reviewed_at IS NOT NULL", leaveId));

        leaves.review(leaveId, approve(true));
        assertEquals("APPROVED", leaveStatus(leaveId));
        assertEquals("CANCELLED", scheduleStatus(generatedId));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM appointment WHERE schedule_id=?", String.class, generatedId));
        assertEquals(0, number("SELECT booked_slots FROM doctor_schedule WHERE id=?", generatedId));
        assertEquals(1, number("SELECT COUNT(*) FROM appointment WHERE schedule_id=? AND appointment_active_key IS NULL "
                + "AND cancelled_at IS NOT NULL", generatedId));
        assertEquals(1, number("SELECT COUNT(*) FROM doctor_leave_schedule WHERE leave_id=? AND schedule_id=?", leaveId, generatedId));
        assertEquals(1, number("SELECT COUNT(*) FROM operation_log WHERE action='DOCTOR_LEAVE_REVIEW'"));
    }

    @Test
    void manualCreateUpdateAndPublicationRespectApprovedLeaveAndAllowHalfOpenEdges() {
        long leaveId = applyLeave(targetStart, targetEnd.plusHours(1));
        leaves.review(leaveId, approve(false));
        var overlapping = scheduleInput(targetStart.plusMinutes(15), targetEnd.plusMinutes(15), "PUBLISHED");
        var createError = assertThrows(BusinessException.class, () -> schedules.create(overlapping));
        assertEquals(409, createError.getHttpStatus());
        assertTrue(createError.getMessage().contains("批准的请假"));
        overlapping.setStatus("DRAFT");
        assertEquals(409, assertThrows(BusinessException.class, () -> schedules.create(overlapping)).getHttpStatus());

        long before = schedules.create(scheduleInput(targetStart.minusHours(1), targetStart, "PUBLISHED")).id();
        long after = schedules.create(scheduleInput(targetEnd.plusHours(1), targetEnd.plusHours(2), "DRAFT")).id();
        assertEquals("PUBLISHED", scheduleStatus(before));
        assertEquals("DRAFT", scheduleStatus(after));

        var updateError = assertThrows(BusinessException.class, () -> schedules.update(after,
                scheduleInput(targetStart.plusMinutes(15), targetEnd.plusMinutes(15), "DRAFT")));
        assertTrue(updateError.getMessage().contains("批准的请假"));
        assertEquals(targetEnd.plusHours(1), timestamp("SELECT start_time FROM doctor_schedule WHERE id=?", after));

        // 模拟历史草稿，验证发布入口自身仍校验请假，而非只依靠创建入口。
        long legacyDraft = seedSchedule(targetStart.plusMinutes(10), targetStart.plusMinutes(40), "DRAFT", 3, 0, 0);
        var publishError = assertThrows(BusinessException.class, () -> schedules.changeStatus(legacyDraft, "PUBLISHED"));
        assertTrue(publishError.getMessage().contains("批准的请假"));
        assertEquals("DRAFT", scheduleStatus(legacyDraft));
        schedules.changeStatus(after, "PUBLISHED");
        assertEquals("PUBLISHED", scheduleStatus(after));
    }

    @Test
    void emptyPreviousWeekFallsBackToMostRecentOlderWeekWithoutChangingWeekdayOrTime() {
        jdbc.update("DELETE FROM doctor_schedule");
        LocalDate recentSourceMonday = targetMonday.minusWeeks(3);
        LocalDateTime recent = atClinic(recentSourceMonday.plusDays(3), 14, 20);
        seedSchedule(recent, recent.plusMinutes(80), "CLOSED", 9, 0, 45);
        LocalDateTime older = atClinic(targetMonday.minusWeeks(4).plusDays(2), 8, 0);
        seedSchedule(older, older.plusHours(1), "PUBLISHED", 2, 0, 0);

        var result = weekly.generate(targetMonday);
        assertTrue(result.successful());
        assertEquals(1, result.created());
        assertEquals(List.of(DOCTOR), result.fallbackDoctorIds());
        LocalDateTime expected = atClinic(targetMonday.plusDays(3), 14, 20);
        long id = scheduleId(expected);
        assertEquals(expected.plusMinutes(80), timestamp("SELECT end_time FROM doctor_schedule WHERE id=?", id));
        assertEquals(9, number("SELECT total_slots FROM doctor_schedule WHERE id=?", id));
        assertEquals(45, number("SELECT cancel_before_minutes FROM doctor_schedule WHERE id=?", id));
        assertEquals(recentSourceMonday, jdbc.queryForObject("SELECT source_monday FROM schedule_generation_item", java.sql.Date.class).toLocalDate());
        assertEquals(1, number("SELECT COUNT(*) FROM doctor_schedule WHERE start_time>=? AND start_time<? AND deleted=0",
                sqlTime(WeeklyScheduleDates.utcStart(targetMonday)), sqlTime(WeeklyScheduleDates.utcStart(targetMonday.plusWeeks(1)))));
    }

    @Test
    void simultaneousGenerationCreatesExactlyOneShiftAndOneLedgerItem() throws Exception {
        var results = concurrently(() -> generator.generate(DOCTOR, targetMonday),
                () -> generator.generate(DOCTOR, targetMonday));
        assertEquals(1, results.first().created() + results.second().created());
        assertEquals(1, results.first().existing() + results.second().existing());
        assertEquals(1, liveSchedules(targetStart));
        assertEquals(1, number("SELECT COUNT(*) FROM schedule_generation_item"));
        assertEquals("PUBLISHED", scheduleStatus(scheduleId(targetStart)));
    }

    @Test
    void concurrentApprovalAndGenerationNeverLeavePublishedShiftInsideLeaveAndNextWeekRecovers() throws Exception {
        long leaveId = applyLeave(targetStart, targetEnd);
        concurrently(() -> leaves.review(leaveId, approve(true)), () -> generator.generate(DOCTOR, targetMonday));
        assertEquals("APPROVED", leaveStatus(leaveId));
        assertEquals(0, number("SELECT COUNT(*) FROM doctor_schedule WHERE doctor_id=10 AND deleted=0 "
                + "AND status='PUBLISHED' AND start_time<? AND end_time>?", sqlTime(targetEnd), sqlTime(targetStart)));
        assertEquals(1, number("SELECT COUNT(*) FROM schedule_generation_item WHERE start_time=?", sqlTime(targetStart)));
        assertTrue(List.of("CREATED", "LEAVE").contains(ledgerOutcome(targetStart)));

        var following = weekly.generate(targetMonday.plusWeeks(1));
        assertTrue(following.successful());
        assertEquals(1, following.created());
        assertEquals("PUBLISHED", scheduleStatus(scheduleId(targetStart.plusWeeks(1))));
    }

    @Test
    void approvedOverlapCurrentReadSeesCommitDespiteExistingRepeatableReadSnapshot() {
        long leaveId = applyLeave(targetStart, targetEnd);
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(20);
        transaction.executeWithoutResult(status -> {
            assertEquals("PENDING", leaveStatus(leaveId)); // 普通读建立 RR 快照，无行锁。
            onAnotherThread(() -> leaves.review(leaveId, approve(false)));
            assertEquals("PENDING", leaveStatus(leaveId)); // 证明确实仍处于旧快照。
            var scheduleMapper = context.getBean(ScheduleMapper.class);
            assertNotNull(scheduleMapper.lockDepartment(DEPARTMENT));
            assertNotNull(scheduleMapper.lockDoctor(DOCTOR));
            assertEquals(1, context.getBean(DoctorLeaveMapper.class).countApprovedOverlaps(DOCTOR, targetStart, targetEnd));
        });
        assertEquals("APPROVED", leaveStatus(leaveId));
    }

    @Test
    void draftCancelledForLeaveIsNotPromotedIntoFollowingWeeksSource() {
        jdbc.update("DELETE FROM doctor_schedule");
        long draft = seedSchedule(targetStart, targetEnd, "DRAFT", 4, 0, 0);
        long leaveId = applyLeave(targetStart, targetEnd);
        leaves.review(leaveId, approve(false));
        assertEquals("CANCELLED", scheduleStatus(draft));
        assertEquals(0, number("SELECT COUNT(*) FROM doctor_leave_schedule WHERE schedule_id=?", draft));
        var result = generator.generate(DOCTOR, targetMonday.plusWeeks(1));
        assertTrue(result.noSource());
        assertEquals(0, result.created());
        assertEquals(0, liveSchedules(targetStart.plusWeeks(1)));
    }

    @Test
    void xxlBootstrapCreatesOneStoppedProjectJobWithoutDemoAccountsOrTasks() throws Exception {
        assertEquals(13316, jdbc.queryForObject("SELECT @@port", Integer.class));
        var deployment = Paths.get("..").resolve("deploy").resolve("xxl-job");
        byte[] schema = Files.readAllBytes(deployment.resolve("init").resolve("00-schema.sql"));
        String bootstrap = Files.readString(deployment.resolve("init").resolve("01-bootstrap.sh"), StandardCharsets.UTF_8);
        var sqlSection = Pattern.compile("(?s)<<SQL\\R(.*?)\\RSQL(?:\\R|$)").matcher(bootstrap);
        assertTrue(sqlSection.find(), "初始化脚本必须包含 SQL heredoc");
        String adminName = "weekly_test_operator";
        String adminPassword = "Dental-Test-42!";
        String executorName = "dental-appointment-test";
        String executorToken = "x".repeat(40);
        String bootstrapSql = sqlSection.group(1)
                .replace("${admin_name_hex}", HexFormat.of().formatHex(adminName.getBytes(StandardCharsets.UTF_8)))
                .replace("${admin_password_hex}", HexFormat.of().formatHex(adminPassword.getBytes(StandardCharsets.UTF_8)))
                .replace("${executor_name_hex}", HexFormat.of().formatHex(executorName.getBytes(StandardCharsets.UTF_8)))
                .replace("${executor_token_hex}", HexFormat.of().formatHex(executorToken.getBytes(StandardCharsets.UTF_8)));
        assertFalse(bootstrapSql.contains("${"), "初始化 SQL 不应有未替换的 shell 变量");

        // USE 只影响这个独立连接；其他 JdbcTemplate 调用仍连接原固定业务测试库。
        try (Connection connection = context.getBean(DataSource.class).getConnection()) {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT @@port")) {
                assertTrue(rows.next());
                assertEquals(13316, rows.getInt(1), "执行官方 DDL 前必须确认独立测试实例端口");
            }
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(schema), StandardCharsets.UTF_8));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ByteArrayResource(bootstrapSql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));

            long executionGroupId;
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT id,app_name,address_type FROM xxl_job.xxl_job_group")) {
                assertTrue(rows.next(), "必须初始化本项目执行器组");
                executionGroupId = rows.getLong("id");
                assertEquals(executorName, rows.getString("app_name"));
                assertEquals(0, rows.getInt("address_type"));
                assertFalse(rows.next(), "不得初始化额外示例执行器");
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT job_group,name,author,schedule_type,schedule_conf,misfire_strategy,"
                         + "executor_route_strategy,executor_handler,executor_param,executor_block_strategy,executor_timeout,"
                         + "executor_fail_retry_count,glue_type,trigger_status,trigger_last_time,trigger_next_time "
                         + "FROM xxl_job.xxl_job_info")) {
                assertTrue(rows.next(), "必须预置本项目周排班任务");
                assertEquals(executionGroupId, rows.getLong("job_group"));
                assertEquals("每周生成医生排班", rows.getString("name"));
                assertEquals(adminName, rows.getString("author"));
                assertEquals("CRON", rows.getString("schedule_type"));
                assertEquals("0 0 0 ? * SUN", rows.getString("schedule_conf"));
                assertEquals("FIRE_ONCE_NOW", rows.getString("misfire_strategy"));
                assertEquals("FIRST", rows.getString("executor_route_strategy"));
                assertEquals("weeklyDoctorScheduleJob", rows.getString("executor_handler"));
                assertEquals("", rows.getString("executor_param"));
                assertEquals("SERIAL_EXECUTION", rows.getString("executor_block_strategy"));
                assertEquals(300, rows.getInt("executor_timeout"));
                assertEquals(3, rows.getInt("executor_fail_retry_count"));
                assertEquals("BEAN", rows.getString("glue_type"));
                assertEquals(0, rows.getInt("trigger_status"));
                assertEquals(0, rows.getLong("trigger_last_time"));
                assertEquals(0, rows.getLong("trigger_next_time"));
                assertFalse(rows.next(), "不得初始化额外示例任务");
            }
            try (var statement = connection.prepareStatement(
                    "SELECT access_token=? AS token_matches FROM xxl_job.xxl_job_group WHERE id=?")) {
                statement.setString(1, executorToken);
                statement.setLong(2, executionGroupId);
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertTrue(rows.getBoolean("token_matches"), "执行器令牌应来自初始化夹具");
                }
            }
            try (var statement = connection.prepareStatement(
                    "SELECT username,role,password=SHA2(?,256) AS password_matches FROM xxl_job.xxl_job_user")) {
                statement.setString(1, adminPassword);
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(adminName, rows.getString("username"));
                    assertEquals(1, rows.getInt("role"));
                    assertTrue(rows.getBoolean("password_matches"), "管理员密码应以 SHA-256 哈希保存");
                    assertFalse(rows.next(), "不得初始化额外默认账号");
                }
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT (SELECT COUNT(*) FROM xxl_job.xxl_job_user WHERE username='admin') AS default_users,"
                            + "(SELECT COUNT(*) FROM xxl_job.xxl_job_info WHERE executor_handler LIKE '%demo%') AS demo_jobs")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt("default_users"));
                assertEquals(0, rows.getInt("demo_jobs"));
            }
        }
        assertEquals(DATABASE, jdbc.queryForObject("SELECT DATABASE()", String.class));
    }

    private long applyLeave(LocalDateTime start, LocalDateTime end) {
        authenticateDoctor();
        try {
            var input = new DoctorLeaveApplyDTO();
            input.setStartTime(TimeUtil.toClinic(start));
            input.setEndTime(TimeUtil.toClinic(end));
            input.setReason("独立数据库测试请假");
            return leaves.apply(input).id();
        } finally {
            authenticateAdmin();
        }
    }

    private long seedSchedule(LocalDateTime start, LocalDateTime end, String status, int capacity, int booked, int cutoff) {
        jdbc.update("INSERT INTO doctor_schedule(doctor_id,department_id,start_time,end_time,total_slots,booked_slots,status,"
                        + "cancel_before_minutes,version,created_by,deleted,created_at,updated_at) "
                        + "VALUES (10,20,?,?,?,?,?,?,0,1,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                sqlTime(start), sqlTime(end), capacity, booked, status, cutoff);
        return scheduleId(start);
    }

    private void seedActiveAppointment(long scheduleId) {
        jdbc.update("INSERT INTO appointment(appointment_no,patient_user_id,patient_profile_id,schedule_id,doctor_id,department_id,"
                        + "patient_name_snapshot,patient_phone_snapshot,doctor_name_snapshot,department_name_snapshot,"
                        + "start_time_snapshot,end_time_snapshot,status,appointment_active_key,deleted,created_at,updated_at) "
                        + "VALUES ('WEEKLY-DB-BOOKING',900,901,?,10,20,'测试就诊人','13800000000','独立测试医生','独立测试口腔科',"
                        + "?,?,'PENDING',?,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                scheduleId, sqlTime(targetStart), sqlTime(targetEnd), "901:" + scheduleId);
        jdbc.update("UPDATE doctor_schedule SET booked_slots=1 WHERE id=?", scheduleId);
    }

    private static DoctorLeaveReviewDTO approve(boolean confirm) {
        var input = new DoctorLeaveReviewDTO();
        input.setApproved(true);
        input.setConfirmCancelAppointments(confirm);
        return input;
    }

    private static ScheduleSaveDTO scheduleInput(LocalDateTime start, LocalDateTime end, String status) {
        var input = new ScheduleSaveDTO();
        input.setDoctorId(DOCTOR);
        input.setStartTime(TimeUtil.toClinic(start));
        input.setEndTime(TimeUtil.toClinic(end));
        input.setTotalSlots(5);
        input.setCancelBeforeMinutes(0);
        input.setStatus(status);
        return input;
    }

    private long scheduleId(LocalDateTime start) {
        return jdbc.queryForObject("SELECT id FROM doctor_schedule WHERE doctor_id=10 AND start_time=? AND deleted=0", Long.class, sqlTime(start));
    }

    private int liveSchedules(LocalDateTime start) {
        return number("SELECT COUNT(*) FROM doctor_schedule WHERE doctor_id=10 AND start_time=? AND deleted=0", sqlTime(start));
    }

    private String scheduleStatus(long id) { return jdbc.queryForObject("SELECT status FROM doctor_schedule WHERE id=?", String.class, id); }
    private String leaveStatus(long id) { return jdbc.queryForObject("SELECT status FROM doctor_leave WHERE id=?", String.class, id); }
    private String ledgerOutcome(LocalDateTime start) {
        return jdbc.queryForObject("SELECT outcome FROM schedule_generation_item WHERE doctor_id=10 AND start_time=?", String.class, sqlTime(start));
    }
    private int number(String sql, Object... arguments) { return jdbc.queryForObject(sql, Integer.class, arguments); }
    private LocalDateTime timestamp(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, LocalDateTime.class, arguments);
    }
    // DATETIME 保存 UTC 的本地字段，用 JDBC 4.2 LocalDateTime 避免 Timestamp/JVM 时区二次换算。
    private static LocalDateTime sqlTime(LocalDateTime time) { return time; }
    private static LocalDateTime atClinic(LocalDate day, int hour, int minute) {
        return TimeUtil.toUtc(day.atTime(LocalTime.of(hour, minute)).atZone(TimeUtil.CLINIC_ZONE).toOffsetDateTime());
    }

    private record Pair<A, B>(A first, B second) { }

    private static <A, B> Pair<A, B> concurrently(Callable<A> first, Callable<B> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<A> a = executor.submit(() -> worker(first, ready, start));
            Future<B> b = executor.submit(() -> worker(second, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "并发测试工作线程未就绪");
            start.countDown();
            return new Pair<>(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "并发测试线程未结束");
        }
    }

    private static <T> T worker(Callable<T> task, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS), "并发测试未释放启动信号");
        authenticateAdmin();
        try { return task.call(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private static <T> T onAnotherThread(Callable<T> task) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(() -> {
                authenticateAdmin();
                try { return task.call(); }
                finally { SecurityContextHolder.clearContext(); }
            }).get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) { Thread.currentThread().interrupt(); }
            throw new AssertionError("独立审批事务失败", exception);
        } finally {
            executor.shutdownNow();
            try {
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "独立审批线程未结束");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待独立审批线程中断", interrupted);
            }
        }
    }

    private static void authenticateAdmin() { authenticate("ADMIN", null, 1L); }
    private static void authenticateDoctor() { authenticate("DOCTOR", DOCTOR, 2L); }
    private static void authenticate(String role, Long doctorId, long userId) {
        var user = new CurrentUser(userId, "weekly-test", "独立测试账号", List.of(role), doctorId, "weekly-db-test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan(annotationClass = Mapper.class, basePackages = "com.dental")
    @Import({ScheduleService.class, AppointmentService.class, OperationLogService.class,
            DoctorLeaveService.class, DoctorWeekGenerator.class, WeeklyScheduleService.class})
    static class DatabaseConfiguration {
        @Bean
        DataSource dataSource() {
            var source = new DriverManagerDataSource();
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            source.setUrl(JDBC_URL);
            source.setUsername("root");
            source.setPassword("");
            return source;
        }

        @Bean(initMethod = "migrate")
        Flyway flyway(DataSource source) {
            return Flyway.configure().dataSource(source).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("7")).cleanDisabled(true).load();
        }

        @Bean
        @DependsOn("flyway")
        SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            var global = new GlobalConfig();
            global.setDbConfig(new GlobalConfig.DbConfig());
            global.setMetaObjectHandler(new AutomaticFieldFill());
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(global);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }

        @Bean
        ClinicService clinicService() {
            // 只有此纯配置方法会被排班调用，无需启动完整诊所/Boot依赖或Mockito agent。
            return new ClinicService(null, null) {
                @Override public int defaultCancelBeforeMinutes() { return 120; }
            };
        }
    }
}
