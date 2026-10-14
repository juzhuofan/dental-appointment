-- Business times are stored as UTC, while week boundaries use Asia/Shanghai.
CREATE TABLE doctor_leave (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 doctor_id BIGINT UNSIGNED NOT NULL,
 start_time DATETIME(3) NOT NULL, end_time DATETIME(3) NOT NULL,
 reason VARCHAR(500) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 reviewed_by BIGINT UNSIGNED, review_remark VARCHAR(500), reviewed_at DATETIME(3),
 deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 KEY idx_leave_overlap(doctor_id,status,deleted,start_time,end_time),
 KEY idx_leave_review(status,deleted,created_at),
 CONSTRAINT ck_leave_period CHECK(end_time>start_time),
 CONSTRAINT ck_leave_status CHECK(status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Distinguish leave-induced cancellations from intentionally cancelled shifts.
CREATE TABLE doctor_leave_schedule (
 leave_id BIGINT UNSIGNED NOT NULL, schedule_id BIGINT UNSIGNED NOT NULL,
 PRIMARY KEY(leave_id,schedule_id), KEY idx_leave_schedule(schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Retain nominal shifts skipped for leave, and tombstones for generated shifts.
-- Deleting a generated schedule must not cause a retry to recreate it.
CREATE TABLE schedule_generation_item (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 target_monday DATE NOT NULL, doctor_id BIGINT UNSIGNED NOT NULL,
 source_monday DATE NOT NULL, source_schedule_id BIGINT UNSIGNED,
 start_time DATETIME(3) NOT NULL, end_time DATETIME(3) NOT NULL,
 total_slots INT UNSIGNED NOT NULL, cancel_before_minutes SMALLINT UNSIGNED NOT NULL,
 outcome VARCHAR(16) NOT NULL, schedule_id BIGINT UNSIGNED,
 created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_generated_shift(doctor_id,start_time,end_time),
 KEY idx_generation_week(target_monday,doctor_id),
 CONSTRAINT ck_generation_outcome CHECK(outcome IN ('CREATED','LEAVE','EXISTING','EXPIRED')),
 CONSTRAINT ck_generation_period CHECK(end_time>start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
