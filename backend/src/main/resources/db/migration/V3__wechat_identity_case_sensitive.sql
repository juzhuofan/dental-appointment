-- 微信 OpenID 区分大小写；账号与活动身份唯一索引必须使用相同的精确比较规则。
ALTER TABLE sys_user
    MODIFY COLUMN wechat_openid VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    MODIFY COLUMN active_openid VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN wechat_openid END) STORED;
