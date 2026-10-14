package com.dental.leave.vo;

import java.time.OffsetDateTime;

public record DoctorLeaveVO(Long id, Long doctorId, String doctorName, String departmentName,
                            OffsetDateTime startTime, OffsetDateTime endTime, String reason,
                            String status, Long reviewedBy, String reviewRemark,
                            OffsetDateTime reviewedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
