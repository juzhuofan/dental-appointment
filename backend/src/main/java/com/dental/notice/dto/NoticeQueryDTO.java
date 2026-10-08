package com.dental.notice.dto;

/** 公告查询条件。 */
public record NoticeQueryDTO(String keyword, Integer status, int page, int size) {
}
