package com.dental.common;

public enum ScheduleState {
    DRAFT,
    PUBLISHED,
    CLOSED,
    CANCELLED;
    public static final String PATTERN = "DRAFT|PUBLISHED|CLOSED|CANCELLED";
}
