package com.dental.notice.vo;

import java.time.OffsetDateTime;

/** 公告响应。 */
public record NoticeVO(Long id, String title, String content, Integer status,
                       OffsetDateTime publishAt, OffsetDateTime expireAt, OffsetDateTime createdAt) {
}
