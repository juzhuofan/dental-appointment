#!/bin/sh
# First-volume initialization only. No demo tasks, default password, or default token.
set -eu

: "${XXL_JOB_ADMIN_USERNAME:?XXL_JOB_ADMIN_USERNAME is required}"
: "${XXL_JOB_ADMIN_PASSWORD:?XXL_JOB_ADMIN_PASSWORD is required}"
: "${XXL_JOB_EXECUTOR_APPNAME:?XXL_JOB_EXECUTOR_APPNAME is required}"
: "${XXL_JOB_EXECUTOR_ACCESS_TOKEN:?XXL_JOB_EXECUTOR_ACCESS_TOKEN is required}"

if [ "${#XXL_JOB_ADMIN_USERNAME}" -lt 4 ] || [ "${#XXL_JOB_ADMIN_USERNAME}" -gt 20 ]; then
  echo 'XXL_JOB_ADMIN_USERNAME must contain 4-20 characters (XXL-JOB login limit)' >&2; exit 1
fi
case "$XXL_JOB_ADMIN_USERNAME" in *[!a-zA-Z0-9_.-]*) echo 'Invalid admin username' >&2; exit 1 ;; esac
if [ "${#XXL_JOB_ADMIN_PASSWORD}" -lt 12 ] || [ "${#XXL_JOB_ADMIN_PASSWORD}" -gt 20 ]; then
  echo 'XXL_JOB_ADMIN_PASSWORD must contain 12-20 characters (XXL-JOB login limit)' >&2; exit 1
fi
if [ "${#XXL_JOB_EXECUTOR_ACCESS_TOKEN}" -lt 32 ] || [ "${#XXL_JOB_EXECUTOR_ACCESS_TOKEN}" -gt 255 ]; then
  echo 'XXL_JOB_EXECUTOR_ACCESS_TOKEN must contain 32-255 characters' >&2; exit 1
fi
case "$XXL_JOB_EXECUTOR_ACCESS_TOKEN" in *[!a-zA-Z0-9_-]*) echo 'Use a random alphanumeric executor token' >&2; exit 1 ;; esac
if [ "${#XXL_JOB_EXECUTOR_APPNAME}" -gt 64 ]; then echo 'Invalid executor AppName' >&2; exit 1; fi
case "$XXL_JOB_EXECUTOR_APPNAME" in *[!a-zA-Z0-9_-]*) echo 'Invalid executor AppName' >&2; exit 1 ;; esac
case "$XXL_JOB_EXECUTOR_APPNAME" in [a-zA-Z0-9]*) ;; *) echo 'Invalid executor AppName' >&2; exit 1 ;; esac

# Hex conversion keeps arbitrary password punctuation out of SQL syntax and command arguments.
to_hex() { printf '%s' "$1" | od -An -tx1 | tr -d ' \n'; }
admin_name_hex=$(to_hex "$XXL_JOB_ADMIN_USERNAME")
admin_password_hex=$(to_hex "$XXL_JOB_ADMIN_PASSWORD")
executor_name_hex=$(to_hex "$XXL_JOB_EXECUTOR_APPNAME")
executor_token_hex=$(to_hex "$XXL_JOB_EXECUTOR_ACCESS_TOKEN")

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket --user=root --database=xxl_job <<SQL
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO xxl_job_user (username, password, role, permission)
VALUES (CONVERT(0x${admin_name_hex} USING utf8mb4), SHA2(CONVERT(0x${admin_password_hex} USING utf8mb4), 256), 1, NULL);
INSERT INTO xxl_job_group (app_name, name, address_type, address_list, access_token, update_time)
VALUES (CONVERT(0x${executor_name_hex} USING utf8mb4), '口腔预约系统执行器', 0, NULL,
        CONVERT(0x${executor_token_hex} USING utf8mb4), NOW());
SET @dental_executor_group_id = LAST_INSERT_ID();
INSERT INTO xxl_job_info
    (job_group, name, author, alarm_email, schedule_type, schedule_conf, misfire_strategy,
     executor_route_strategy, executor_handler, executor_param, executor_block_strategy,
     executor_timeout, executor_fail_retry_count, glue_type, glue_source, glue_remark,
     glue_updatetime, child_jobid, trigger_status, trigger_last_time, trigger_next_time,
     add_time, update_time)
VALUES
    (@dental_executor_group_id, '每周生成医生排班', CONVERT(0x${admin_name_hex} USING utf8mb4), '',
     'CRON', '0 0 0 ? * SUN', 'FIRE_ONCE_NOW', 'FIRST', 'weeklyDoctorScheduleJob', '',
     'SERIAL_EXECUTION', 300, 3, 'BEAN', '', '预置周排班任务，验收后手动启用', NOW(), '', 0, 0, 0,
     NOW(), NOW());
COMMIT;
SQL

echo 'XXL-JOB administrator, execution group and weekly schedule job initialized; the job remains stopped.'
