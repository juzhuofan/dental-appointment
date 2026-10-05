package com.dental.web;

import com.dental.common.AppointmentState;
import com.dental.common.ScheduleState;
import com.dental.common.UserRole;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 独立请求 DTO，不直接接受数据库行。 */
public final class Requests {
    private Requests() {}

    public record Login(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 100) String password) {}

    public record DemoLogin(@NotBlank @Size(min = 8, max = 100) String deviceId) {}

    public record Password(
            @NotBlank @Size(max = 100) String oldPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record Profile(
            @NotBlank @Size(max = 80) String realName,
            @NotBlank @Pattern(regexp = "1[3-9]\\d{9}", message = "请输入11位手机号") String phone,
            @Min(0) @Max(2) Integer gender,
            @PastOrPresent LocalDate birthDate,
            @Size(max = 255) String remark) {}

    public record Department(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 1000) String description,
            @NotNull @Min(0) Integer sortOrder,
            @NotNull @Min(0) @Max(1) Integer status) {}

    public record Doctor(
            @Positive Long userId,
            @NotNull @Positive Long departmentId,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 80) String title,
            @Size(max = 500) String specialty,
            @Size(max = 10000) String introduction,
            @Size(max = 500) String avatarUrl,
            @NotNull @Min(0) @Max(1) Integer status) {}

    public record Schedule(
            @NotNull @Positive Long doctorId,
            @NotNull OffsetDateTime startTime,
            @NotNull OffsetDateTime endTime,
            @NotNull @Min(1) @Max(1000) Integer totalSlots,
            @NotBlank @Pattern(regexp = ScheduleState.PATTERN) String status,
            @Min(0) @Max(10080) Integer cancelBeforeMinutes) {}

    public record ScheduleStatus(
            @NotBlank @Pattern(regexp = ScheduleState.PATTERN) String status) {}

    public record Appointment(
            @NotNull @Positive Long scheduleId,
            @NotNull @Positive Long patientProfileId,
            @Size(max = 500) String chiefComplaint) {}

    public record Cancel(@Size(max = 255) String reason) {}

    public record AppointmentStatus(
            @NotBlank @Pattern(regexp = AppointmentState.TARGET_PATTERN) String status,
            @Size(max = 255) String reason) {}

    public record Notice(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 10000) String content,
            @NotNull @Min(0) @Max(2) Integer status,
            OffsetDateTime publishAt,
            OffsetDateTime expireAt) {}

    public record User(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}") String username,
            @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Pattern(regexp = UserRole.PATTERN) String role,
            @NotNull @Min(0) @Max(1) Integer status) {}

    public record Clinic(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 30) String phone,
            @NotBlank @Size(max = 300) String address,
            @NotBlank @Size(max = 100) String openingHours,
            @NotBlank @Size(max = 2000) String introduction,
            @NotNull @Min(0) @Max(10080) Integer cancelBeforeMinutes) {}
}
