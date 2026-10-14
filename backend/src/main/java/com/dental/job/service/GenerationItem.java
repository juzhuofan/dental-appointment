package com.dental.job.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Immutable generation ledger. Outcome LEAVE preserves the nominal shift for the following week. */
public record GenerationItem(LocalDate targetMonday, long doctorId, LocalDate sourceMonday,
        Long sourceScheduleId, LocalDateTime startTime, LocalDateTime endTime, int totalSlots,
        int cancelBeforeMinutes, String outcome, Long scheduleId, LocalDateTime createdAt) { }
