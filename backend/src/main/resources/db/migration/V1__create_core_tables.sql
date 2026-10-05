CREATE TABLE sys_user (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(64) NOT NULL, password_hash VARCHAR(100) NOT NULL, phone VARCHAR(20),
 wechat_openid VARCHAR(128), demo_device_hash CHAR(64), display_name VARCHAR(80) NOT NULL,
 avatar_url VARCHAR(500), status TINYINT NOT NULL DEFAULT 1, last_login_at DATETIME(3),
 deleted TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 active_username VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN username END) STORED,
 active_phone VARCHAR(20) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN phone END) STORED,
 active_openid VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN wechat_openid END) STORED,
 active_device_hash CHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN demo_device_hash END) STORED,
 UNIQUE KEY uk_user_name(active_username), UNIQUE KEY uk_user_phone(active_phone),
 UNIQUE KEY uk_user_openid(active_openid), UNIQUE KEY uk_user_device(active_device_hash),
 KEY idx_user_status(status,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_role (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, role_code VARCHAR(32) NOT NULL,
 role_name VARCHAR(64) NOT NULL, description VARCHAR(255),
 deleted TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_role_code(role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sys_user_role (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, user_id BIGINT UNSIGNED NOT NULL, role_id BIGINT UNSIGNED NOT NULL,
 deleted TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_user_role(user_id,role_id), KEY idx_role_user(role_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE patient_profile (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, user_id BIGINT UNSIGNED NOT NULL,
 real_name VARCHAR(80) NOT NULL, phone VARCHAR(20) NOT NULL, gender TINYINT NOT NULL DEFAULT 0,
 birth_date DATE, remark VARCHAR(255),
 deleted TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_patient_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE department (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, name VARCHAR(80) NOT NULL, description VARCHAR(1000),
 sort_order INT NOT NULL DEFAULT 0, status TINYINT NOT NULL DEFAULT 1,
 deleted TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 active_name VARCHAR(80) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN name END) STORED,
 UNIQUE KEY uk_department_name(active_name), KEY idx_department_status(status,sort_order,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE doctor (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, user_id BIGINT UNSIGNED, department_id BIGINT UNSIGNED NOT NULL,
 name VARCHAR(80) NOT NULL, title VARCHAR(80), specialty VARCHAR(500), introduction TEXT, avatar_url VARCHAR(500),
 status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 active_user_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN user_id END) STORED,
 UNIQUE KEY uk_doctor_user(active_user_id), KEY idx_doctor_department(department_id,status,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE doctor_schedule (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, doctor_id BIGINT UNSIGNED NOT NULL, department_id BIGINT UNSIGNED NOT NULL,
 start_time DATETIME(3) NOT NULL, end_time DATETIME(3) NOT NULL, total_slots INT UNSIGNED NOT NULL,
 booked_slots INT UNSIGNED NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL,
 cancel_before_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 120, version INT UNSIGNED NOT NULL DEFAULT 0,
 created_by BIGINT UNSIGNED NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 active_period VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN CONCAT(doctor_id,':',start_time,':',end_time) END) STORED,
 UNIQUE KEY uk_schedule_period(active_period), KEY idx_schedule_search(department_id,status,start_time,deleted),
 KEY idx_schedule_doctor(doctor_id,start_time,deleted),
 CONSTRAINT ck_schedule_period CHECK(end_time>start_time), CONSTRAINT ck_schedule_slots CHECK(booked_slots<=total_slots AND total_slots>0),
 CONSTRAINT ck_schedule_status CHECK(status IN ('DRAFT','PUBLISHED','CLOSED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE appointment (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, appointment_no VARCHAR(32) NOT NULL,
 patient_user_id BIGINT UNSIGNED NOT NULL, patient_profile_id BIGINT UNSIGNED NOT NULL,
 schedule_id BIGINT UNSIGNED NOT NULL, doctor_id BIGINT UNSIGNED NOT NULL, department_id BIGINT UNSIGNED NOT NULL,
 patient_name_snapshot VARCHAR(80) NOT NULL, patient_phone_snapshot VARCHAR(20) NOT NULL,
 doctor_name_snapshot VARCHAR(80) NOT NULL, department_name_snapshot VARCHAR(80) NOT NULL,
 start_time_snapshot DATETIME(3) NOT NULL, end_time_snapshot DATETIME(3) NOT NULL, chief_complaint VARCHAR(500),
 status VARCHAR(16) NOT NULL, appointment_active_key VARCHAR(80), cancel_reason VARCHAR(255),
 cancelled_at DATETIME(3), handled_by BIGINT UNSIGNED, handled_at DATETIME(3), deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_appointment_no(appointment_no), UNIQUE KEY uk_appointment_active_key(appointment_active_key),
 KEY idx_appointment_patient(patient_user_id,status,created_at,deleted), KEY idx_appointment_schedule(schedule_id,status,deleted),
 KEY idx_appointment_doctor(doctor_id,start_time_snapshot,deleted),
 CONSTRAINT ck_appointment_status CHECK(status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED','NO_SHOW')),
 CONSTRAINT ck_appointment_active CHECK((deleted=0 AND status IN ('PENDING','CONFIRMED') AND appointment_active_key IS NOT NULL) OR ((deleted=1 OR status NOT IN ('PENDING','CONFIRMED')) AND appointment_active_key IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE clinic_notice (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, title VARCHAR(120) NOT NULL, content TEXT NOT NULL,
 status TINYINT NOT NULL DEFAULT 0, publish_at DATETIME(3), expire_at DATETIME(3), created_by BIGINT UNSIGNED NOT NULL,
 deleted TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 KEY idx_notice_public(status,publish_at,expire_at,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE operation_log (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, operator_user_id BIGINT UNSIGNED, operator_name VARCHAR(80),
 operator_role VARCHAR(32), action VARCHAR(64) NOT NULL, target_type VARCHAR(64) NOT NULL, target_id VARCHAR(64),
 summary VARCHAR(500), ip_address VARCHAR(45), deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 KEY idx_log_operator(operator_user_id,created_at), KEY idx_log_target(target_type,target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE system_config (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, config_key VARCHAR(100) NOT NULL, config_value VARCHAR(2000) NOT NULL,
 description VARCHAR(255), updated_by BIGINT UNSIGNED, deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, UNIQUE KEY uk_config_key(config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE auth_token (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, token_id VARCHAR(64) NOT NULL, user_id BIGINT UNSIGNED NOT NULL,
 expires_at DATETIME(3) NOT NULL, revoked TINYINT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_token_id(token_id), KEY idx_token_user(user_id,revoked,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE appointment_idempotency (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, user_id BIGINT UNSIGNED NOT NULL,
 request_key VARCHAR(100) NOT NULL, request_hash CHAR(64) NOT NULL, appointment_id BIGINT UNSIGNED,
 deleted TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_idempotency_user_key(user_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
