package com.dental.schedule.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dental.appointment.service.AppointmentService;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.common.TimeUtil;
import com.dental.department.service.ClinicService;
import com.dental.leave.mapper.DoctorLeaveMapper;
import com.dental.schedule.dto.ScheduleSaveDTO;
import com.dental.schedule.entity.ScheduleEntity;
import com.dental.schedule.mapper.ScheduleMapper;
import com.dental.schedule.vo.DoctorAssignmentVO;
import com.dental.security.CurrentUser;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 手动排班的三个写入口均受已批准请假保护；不连接数据库。 */
@ExtendWith(MockitoExtension.class)
class ScheduleLeaveGuardTest {
    private static final long DOCTOR_ID = 10L;
    private static final long DEPARTMENT_ID = 11L;
    private static final long SCHEDULE_ID = 101L;
    @Mock private ScheduleMapper schedules;
    @Mock private AppointmentService appointments;
    @Mock private OperationLogService audit;
    @Mock private ClinicService clinic;
    @Mock private DoctorLeaveMapper leaves;
    private ScheduleService service;
    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUp() {
        service = new ScheduleService(schedules, appointments, audit, clinic, leaves);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser(1L, "admin", "管理员", List.of("ADMIN"), null, "test"), null, List.of()));
        start = LocalDateTime.now(ZoneOffset.UTC).plusDays(10).withNano(0);
        end = start.plusHours(1);
        DoctorAssignmentVO doctor = new DoctorAssignmentVO();
        doctor.setDoctorId(DOCTOR_ID);
        doctor.setDepartmentId(DEPARTMENT_ID);
        doctor.setDoctorStatus(1);
        when(schedules.findDoctor(DOCTOR_ID)).thenReturn(doctor);
        when(schedules.lockDepartment(DEPARTMENT_ID)).thenReturn(1);
        when(schedules.lockDoctor(DOCTOR_ID)).thenReturn(doctor);
    }

    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "PUBLISHED"})
    void approvedLeaveRejectsNewDraftAndPublishedScheduleBeforeInsert(String status) {
        when(leaves.countApprovedOverlaps(DOCTOR_ID, start, end)).thenReturn(1L);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.create(input(status)));

        assertEquals(409, failure.getHttpStatus());
        verify(schedules, never()).insert(any(ScheduleEntity.class));
        verify(schedules, never()).countOverlaps(eq(DOCTOR_ID), any(), any(), any());
    }

    @Test
    void approvedLeaveRejectsScheduleUpdateBeforeWriting() {
        when(schedules.selectById(SCHEDULE_ID)).thenReturn(schedule("PUBLISHED"));
        when(leaves.countApprovedOverlaps(DOCTOR_ID, start, end)).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.update(SCHEDULE_ID, input("PUBLISHED")));

        verify(schedules, never()).updateById(any(ScheduleEntity.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "CLOSED"})
    void approvedLeaveRejectsPublishAndReopenBeforeChangingStatus(String status) {
        ScheduleEntity existing = schedule(status);
        when(schedules.selectById(SCHEDULE_ID)).thenReturn(existing);
        when(schedules.lockSchedule(SCHEDULE_ID)).thenReturn(existing);
        when(leaves.countApprovedOverlaps(DOCTOR_ID, start, end)).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.changeStatus(SCHEDULE_ID, "PUBLISHED"));

        verify(schedules, never()).updateStatus(eq(SCHEDULE_ID), any(), any());
    }

    @Test
    void zeroApprovedOverlapAllowsCreateAndForwardsExactUtcBoundaries() {
        ScheduleEntity created = schedule("PUBLISHED");
        when(leaves.countApprovedOverlaps(DOCTOR_ID, start, end)).thenReturn(0L);
        when(schedules.insert(any(ScheduleEntity.class))).thenAnswer(call -> {
            ScheduleEntity value = call.getArgument(0);
            value.setId(SCHEDULE_ID);
            return 1;
        });
        when(schedules.lockSchedule(SCHEDULE_ID)).thenReturn(created);

        var result = service.create(input("PUBLISHED"));

        assertEquals(SCHEDULE_ID, result.id());
        verify(leaves).countApprovedOverlaps(DOCTOR_ID, start, end);
        verify(schedules).insert(any(ScheduleEntity.class));
    }

    @Test
    void zeroApprovedOverlapAllowsUpdateWithoutChangingOccupiedSlots() {
        ScheduleEntity existing = schedule("PUBLISHED");
        existing.setBookedSlots(2);
        when(schedules.selectById(SCHEDULE_ID)).thenReturn(existing);
        when(schedules.lockSchedule(SCHEDULE_ID)).thenReturn(existing);
        when(leaves.countApprovedOverlaps(DOCTOR_ID, start, end)).thenReturn(0L);

        var result = service.update(SCHEDULE_ID, input("PUBLISHED"));

        assertEquals(2, result.bookedSlots());
        verify(schedules).updateById(existing);
    }

    @Test
    void zeroApprovedOverlapAllowsReopenAndKeepsExistingBookings() {
        ScheduleEntity existing = schedule("CLOSED");
        existing.setBookedSlots(2);
        when(schedules.selectById(SCHEDULE_ID)).thenReturn(existing);
        when(schedules.lockSchedule(SCHEDULE_ID)).thenReturn(existing);
        when(leaves.countApprovedOverlaps(DOCTOR_ID, start, end)).thenReturn(0L);
        when(schedules.updateStatus(eq(SCHEDULE_ID), eq("PUBLISHED"), any())).thenAnswer(call -> {
            existing.setStatus("PUBLISHED");
            return 1;
        });

        var result = service.changeStatus(SCHEDULE_ID, "PUBLISHED");

        assertEquals("PUBLISHED", result.status());
        assertEquals(2, result.bookedSlots());
        verify(appointments, never()).cancelActiveBySchedule(SCHEDULE_ID);
    }

    private ScheduleSaveDTO input(String status) {
        ScheduleSaveDTO input = new ScheduleSaveDTO();
        input.setDoctorId(DOCTOR_ID);
        input.setStartTime(TimeUtil.toClinic(start));
        input.setEndTime(TimeUtil.toClinic(end));
        input.setTotalSlots(5);
        input.setCancelBeforeMinutes(120);
        input.setStatus(status);
        return input;
    }

    private ScheduleEntity schedule(String status) {
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setId(SCHEDULE_ID);
        schedule.setDoctorId(DOCTOR_ID);
        schedule.setDepartmentId(DEPARTMENT_ID);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setTotalSlots(5);
        schedule.setBookedSlots(0);
        schedule.setCancelBeforeMinutes(120);
        schedule.setVersion(0);
        schedule.setStatus(status);
        return schedule;
    }
}
