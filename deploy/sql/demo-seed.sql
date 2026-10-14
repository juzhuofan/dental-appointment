-- Dental Appointment demo seed data
-- MySQL 8.0; requires Flyway migrations V1-V5 to have been applied.
-- Additive and re-runnable: does not DROP/TRUNCATE tables or overwrite existing clinic rows.
-- All people and contact details below are fictional. The demo patient login is disabled.
-- Run only in a demo/test database; future schedules and sample appointments will appear in the app.

USE dental_appointment;
START TRANSACTION;

-- Use a persistent anchor so running this file again does not create a new batch of schedules.
-- The first batch is scheduled about 30 days after the first execution.
SET @clinic_today := DATE(CONVERT_TZ(UTC_TIMESTAMP(3), '+00:00', '+08:00'));
SET @initial_anchor := DATE_ADD(@clinic_today, INTERVAL 30 DAY);
INSERT IGNORE INTO system_config
    (config_key, config_value, description, deleted, created_at, updated_at)
SELECT 'demo.sql.seed.anchor_date', DATE_FORMAT(@initial_anchor, '%Y-%m-%d'),
       'SQL 演示数据固定日期锚点；勿用于生产诊所资料', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
;
SET @seed_anchor := STR_TO_DATE(
    (SELECT config_value FROM system_config
     WHERE config_key = 'demo.sql.seed.anchor_date' AND deleted = 0 LIMIT 1),
    '%Y-%m-%d'
);

-- Fictional departments (keeps an existing department's fields/status unchanged).
INSERT IGNORE INTO department
    (name, description, sort_order, status, deleted, created_at, updated_at)
VALUES ('牙体牙髓科', '牙痛检查、牙体修复与根管治疗（演示）', 10, 1, 0,
        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO department
    (name, description, sort_order, status, deleted, created_at, updated_at)
VALUES ('口腔修复科', '牙体缺损与修复咨询（演示）', 20, 1, 0,
        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO department
    (name, description, sort_order, status, deleted, created_at, updated_at)
VALUES ('口腔正畸科', '牙列矫治与正畸方案咨询（演示）', 30, 1, 0,
        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

SET @dept_endo := (SELECT id FROM department WHERE name = '牙体牙髓科' AND deleted = 0 ORDER BY id LIMIT 1);
SET @dept_rest := (SELECT id FROM department WHERE name = '口腔修复科' AND deleted = 0 ORDER BY id LIMIT 1);
SET @dept_ortho := (SELECT id FROM department WHERE name = '口腔正畸科' AND deleted = 0 ORDER BY id LIMIT 1);

-- Fictional doctor profiles. No doctor login accounts or passwords are created.
SET @existing_doctor := (SELECT id FROM doctor WHERE department_id = @dept_endo AND name = '林沐（演示）' AND deleted = 0 ORDER BY id LIMIT 1);
INSERT INTO doctor
    (user_id, department_id, name, title, specialty, introduction, status, deleted, created_at, updated_at)
SELECT NULL, @dept_endo, '林沐（演示）', '主治医师', '牙体牙髓疾病与根管治疗',
       '毕业设计使用的虚构演示医生资料，不对应真实医务人员。', 1, 0,
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE @dept_endo IS NOT NULL AND @existing_doctor IS NULL;

SET @existing_doctor := (SELECT id FROM doctor WHERE department_id = @dept_rest AND name = '周安（演示）' AND deleted = 0 ORDER BY id LIMIT 1);
INSERT INTO doctor
    (user_id, department_id, name, title, specialty, introduction, status, deleted, created_at, updated_at)
SELECT NULL, @dept_rest, '周安（演示）', '主治医师', '牙体修复与义齿咨询',
       '毕业设计使用的虚构演示医生资料，不对应真实医务人员。', 1, 0,
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE @dept_rest IS NOT NULL AND @existing_doctor IS NULL;

SET @existing_doctor := (SELECT id FROM doctor WHERE department_id = @dept_ortho AND name = '陈星（演示）' AND deleted = 0 ORDER BY id LIMIT 1);
INSERT INTO doctor
    (user_id, department_id, name, title, specialty, introduction, status, deleted, created_at, updated_at)
SELECT NULL, @dept_ortho, '陈星（演示）', '主治医师', '牙列矫治与正畸咨询',
       '毕业设计使用的虚构演示医生资料，不对应真实医务人员。', 1, 0,
       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE @dept_ortho IS NOT NULL AND @existing_doctor IS NULL;

SET @doctor_endo := (SELECT id FROM doctor WHERE department_id = @dept_endo AND name = '林沐（演示）' AND deleted = 0 ORDER BY id LIMIT 1);
SET @doctor_rest := (SELECT id FROM doctor WHERE department_id = @dept_rest AND name = '周安（演示）' AND deleted = 0 ORDER BY id LIMIT 1);
SET @doctor_ortho := (SELECT id FROM doctor WHERE department_id = @dept_ortho AND name = '陈星（演示）' AND deleted = 0 ORDER BY id LIMIT 1);

-- A disabled fictional patient account allows realistic profile/appointment rows without
-- introducing a usable demo credential. Its password_hash is deliberately non-authenticating.
INSERT IGNORE INTO sys_user
    (username, password_hash, display_name, status, deleted, created_at, updated_at)
VALUES ('__sql_demo_patient__', '!DISABLED_DEMO_DATA_NO_LOGIN!', 'SQL 演示患者账号（禁用）',
        0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

SET @patient_user := (SELECT id FROM sys_user WHERE username = '__sql_demo_patient__' AND deleted = 0 LIMIT 1);

INSERT IGNORE INTO sys_user_role (user_id, role_id, deleted, created_at, updated_at)
SELECT @patient_user, r.id, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_role r
WHERE r.role_code = 'PATIENT'
;

-- Two fictional patient profiles demonstrate one account managing family members.
SET @profile_patient := (SELECT id FROM patient_profile WHERE user_id = @patient_user AND real_name = '演示患者甲（虚构）' AND deleted = 0 ORDER BY id LIMIT 1);
INSERT INTO patient_profile
    (user_id, is_default, real_name, phone, gender, birth_date, remark, deleted, created_at, updated_at)
SELECT @patient_user, 1, '演示患者甲（虚构）', '00000000000', 1, '1990-01-01',
       'SQL 演示资料，不是真实患者；禁用账号。', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE @patient_user IS NOT NULL AND @profile_patient IS NULL;

SET @profile_patient := (SELECT id FROM patient_profile WHERE user_id = @patient_user AND real_name = '演示患者甲（虚构）' AND deleted = 0 ORDER BY id LIMIT 1);
SET @profile_family := (SELECT id FROM patient_profile WHERE user_id = @patient_user AND real_name = '演示家属乙（虚构）' AND deleted = 0 ORDER BY id LIMIT 1);
INSERT INTO patient_profile
    (user_id, is_default, real_name, phone, gender, birth_date, remark, deleted, created_at, updated_at)
SELECT @patient_user, 0, '演示家属乙（虚构）', '00000000000', 2, '2015-06-15',
       'SQL 演示资料，不是真实患者；禁用账号。', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE @patient_user IS NOT NULL AND @profile_family IS NULL;

SET @profile_patient := (SELECT id FROM patient_profile WHERE user_id = @patient_user AND real_name = '演示患者甲（虚构）' AND deleted = 0 ORDER BY id LIMIT 1);
SET @profile_family := (SELECT id FROM patient_profile WHERE user_id = @patient_user AND real_name = '演示家属乙（虚构）' AND deleted = 0 ORDER BY id LIMIT 1);
SET @creator_id := COALESCE(
    (SELECT u.id FROM sys_user u
     JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
     JOIN sys_role r ON r.id = ur.role_id AND r.role_code = 'ADMIN'
     WHERE u.deleted = 0 AND u.status = 1 ORDER BY u.id LIMIT 1),
    @patient_user, 0
);

-- Three published slots per fictional doctor, at 10:00/12:00/15:00 China time.
-- Database DATETIME values are stored in UTC, matching the application configuration.
SET @d0 := @seed_anchor;
SET @d1 := DATE_ADD(@seed_anchor, INTERVAL 1 DAY);
SET @d2 := DATE_ADD(@seed_anchor, INTERVAL 2 DAY);
SET @endo_s0 := TIMESTAMPADD(HOUR, -8, CAST(CONCAT(@d0, ' 10:00:00') AS DATETIME));
SET @endo_s1 := TIMESTAMPADD(HOUR, -8, CAST(CONCAT(@d1, ' 10:00:00') AS DATETIME));
SET @endo_s2 := TIMESTAMPADD(HOUR, -8, CAST(CONCAT(@d2, ' 10:00:00') AS DATETIME));
SET @rest_s0 := TIMESTAMPADD(HOUR, -8, CAST(CONCAT(@d0, ' 12:00:00') AS DATETIME));
SET @rest_s1 := TIMESTAMPADD(HOUR, -8, CAST(CONCAT(@d1, ' 12:00:00') AS DATETIME));
SET @rest_s2 := TIMESTAMPADD(HOUR, -8, CAST(CONCAT(@d2, ' 12:00:00') AS DATETIME));
SET @ortho_s0 := TIMESTAMPADD(HOUR, -8, CAST(CONCAT(@d0, ' 15:00:00') AS DATETIME));
SET @ortho_s1 := TIMESTAMPADD(HOUR, -8, CAST(CONCAT(@d1, ' 15:00:00') AS DATETIME));
SET @ortho_s2 := TIMESTAMPADD(HOUR, -8, CAST(CONCAT(@d2, ' 15:00:00') AS DATETIME));

INSERT IGNORE INTO doctor_schedule
    (doctor_id, department_id, start_time, end_time, total_slots, booked_slots, status,
     cancel_before_minutes, version, created_by, deleted, created_at, updated_at)
SELECT @doctor_endo, @dept_endo, s.start_time, DATE_ADD(s.start_time, INTERVAL 30 MINUTE),
       8, 0, 'PUBLISHED', 120, 0, @creator_id, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT @endo_s0 AS start_time UNION ALL SELECT @endo_s1 UNION ALL SELECT @endo_s2) s
WHERE @doctor_endo IS NOT NULL;

INSERT IGNORE INTO doctor_schedule
    (doctor_id, department_id, start_time, end_time, total_slots, booked_slots, status,
     cancel_before_minutes, version, created_by, deleted, created_at, updated_at)
SELECT @doctor_rest, @dept_rest, s.start_time, DATE_ADD(s.start_time, INTERVAL 30 MINUTE),
       8, 0, 'PUBLISHED', 120, 0, @creator_id, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT @rest_s0 AS start_time UNION ALL SELECT @rest_s1 UNION ALL SELECT @rest_s2) s
WHERE @doctor_rest IS NOT NULL;

INSERT IGNORE INTO doctor_schedule
    (doctor_id, department_id, start_time, end_time, total_slots, booked_slots, status,
     cancel_before_minutes, version, created_by, deleted, created_at, updated_at)
SELECT @doctor_ortho, @dept_ortho, s.start_time, DATE_ADD(s.start_time, INTERVAL 30 MINUTE),
       8, 0, 'PUBLISHED', 120, 0, @creator_id, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT @ortho_s0 AS start_time UNION ALL SELECT @ortho_s1 UNION ALL SELECT @ortho_s2) s
WHERE @doctor_ortho IS NOT NULL;

-- Two closed historical slots back the sample appointments; they cannot be booked now.
SET @past1 := DATE_SUB(@seed_anchor, INTERVAL 37 DAY);
SET @past2 := DATE_SUB(@seed_anchor, INTERVAL 35 DAY);
SET @past_endo_start := TIMESTAMPADD(HOUR, -8, CAST(CONCAT(@past1, ' 10:00:00') AS DATETIME));
SET @past_rest_start := TIMESTAMPADD(HOUR, -8, CAST(CONCAT(@past2, ' 12:00:00') AS DATETIME));

INSERT IGNORE INTO doctor_schedule
    (doctor_id, department_id, start_time, end_time, total_slots, booked_slots, status,
     cancel_before_minutes, version, created_by, deleted, created_at, updated_at)
SELECT @doctor_endo, @dept_endo, @past_endo_start, DATE_ADD(@past_endo_start, INTERVAL 30 MINUTE),
       8, 0, 'CLOSED', 120, 0, @creator_id, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE @doctor_endo IS NOT NULL;

INSERT IGNORE INTO doctor_schedule
    (doctor_id, department_id, start_time, end_time, total_slots, booked_slots, status,
     cancel_before_minutes, version, created_by, deleted, created_at, updated_at)
SELECT @doctor_rest, @dept_rest, @past_rest_start, DATE_ADD(@past_rest_start, INTERVAL 30 MINUTE),
       8, 0, 'CLOSED', 120, 0, @creator_id, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE @doctor_rest IS NOT NULL;

SET @past_endo_schedule := (SELECT id FROM doctor_schedule WHERE doctor_id = @doctor_endo AND start_time = @past_endo_start AND deleted = 0 LIMIT 1);
SET @past_rest_schedule := (SELECT id FROM doctor_schedule WHERE doctor_id = @doctor_rest AND start_time = @past_rest_start AND deleted = 0 LIMIT 1);

-- A completed and a cancelled fictional appointment for admin-page demonstrations.
INSERT IGNORE INTO appointment
    (appointment_no, patient_user_id, patient_profile_id, schedule_id, doctor_id, department_id,
     patient_name_snapshot, patient_phone_snapshot, doctor_name_snapshot, department_name_snapshot,
     start_time_snapshot, end_time_snapshot, chief_complaint, status, appointment_active_key,
     cancel_reason, cancelled_at, handled_by, handled_at, deleted, created_at, updated_at)
SELECT 'DEMO-SEED-COMPLETED-0001', @patient_user, @profile_patient, @past_endo_schedule,
       @doctor_endo, @dept_endo, '演示患者甲（虚构）', '00000000000', '林沐（演示）', '牙体牙髓科',
       @past_endo_start, DATE_ADD(@past_endo_start, INTERVAL 30 MINUTE), 'SQL 虚构演示预约：牙齿检查',
       'COMPLETED', NULL, NULL, NULL, @creator_id, DATE_ADD(@past_endo_start, INTERVAL 30 MINUTE),
       0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE @past_endo_schedule IS NOT NULL AND @profile_patient IS NOT NULL;

INSERT IGNORE INTO appointment
    (appointment_no, patient_user_id, patient_profile_id, schedule_id, doctor_id, department_id,
     patient_name_snapshot, patient_phone_snapshot, doctor_name_snapshot, department_name_snapshot,
     start_time_snapshot, end_time_snapshot, chief_complaint, status, appointment_active_key,
     cancel_reason, cancelled_at, handled_by, handled_at, deleted, created_at, updated_at)
SELECT 'DEMO-SEED-CANCELLED-0001', @patient_user, @profile_family, @past_rest_schedule,
       @doctor_rest, @dept_rest, '演示家属乙（虚构）', '00000000000', '周安（演示）', '口腔修复科',
       @past_rest_start, DATE_ADD(@past_rest_start, INTERVAL 30 MINUTE), 'SQL 虚构演示预约：修复咨询',
       'CANCELLED', NULL, 'SQL 虚构演示记录', DATE_SUB(@past_rest_start, INTERVAL 1 DAY),
       NULL, NULL, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
WHERE @past_rest_schedule IS NOT NULL AND @profile_family IS NOT NULL;

COMMIT;

-- Summary for the SQL console; these are row counts only, no personal data.
SELECT 'departments' AS seeded_table, COUNT(*) AS demo_rows
FROM department WHERE deleted = 0 AND name IN ('牙体牙髓科', '口腔修复科', '口腔正畸科')
UNION ALL
SELECT 'doctors', COUNT(*) FROM doctor
WHERE deleted = 0 AND name IN ('林沐（演示）', '周安（演示）', '陈星（演示）')
UNION ALL
SELECT 'future_demo_schedules', COUNT(*) FROM doctor_schedule
WHERE deleted = 0 AND doctor_id IN (@doctor_endo, @doctor_rest, @doctor_ortho)
  AND start_time >= UTC_TIMESTAMP(3)
UNION ALL
SELECT 'demo_appointments', COUNT(*) FROM appointment
WHERE appointment_no IN ('DEMO-SEED-COMPLETED-0001', 'DEMO-SEED-CANCELLED-0001');
