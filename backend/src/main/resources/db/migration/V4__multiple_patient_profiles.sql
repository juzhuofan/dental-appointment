ALTER TABLE patient_profile
    DROP INDEX uk_patient_user,
    ADD COLUMN is_default TINYINT NOT NULL DEFAULT 0 AFTER user_id,
    ADD COLUMN active_default_user_id BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN deleted = 0 AND is_default = 1 THEN user_id END) STORED,
    ADD UNIQUE KEY uk_patient_default(active_default_user_id),
    ADD KEY idx_patient_user(user_id, deleted, id);

UPDATE patient_profile
SET is_default = 1, updated_at = UTC_TIMESTAMP(3)
WHERE deleted = 0;

UPDATE appointment
SET appointment_active_key = CONCAT(patient_profile_id, ':', schedule_id),
    updated_at = UTC_TIMESTAMP(3)
WHERE deleted = 0 AND status IN ('PENDING', 'CONFIRMED');
