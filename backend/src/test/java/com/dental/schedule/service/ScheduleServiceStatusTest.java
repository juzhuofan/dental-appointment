package com.dental.schedule.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dental.appointment.service.AppointmentService;
import com.dental.audit.service.OperationLogService;
import com.dental.department.service.ClinicService;
import com.dental.schedule.entity.ScheduleEntity;
import com.dental.schedule.mapper.ScheduleMapper;
import com.dental.security.CurrentUser;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 关闭暂停新预约；停诊、删除和人员停用才取消活动预约。 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceStatusTest {

    private static final long SCHEDULE_ID = 101L;
    @Mock private ScheduleMapper schedules;
    @Mock private AppointmentService appointments;
    @Mock private OperationLogService audit;
    @Mock private ClinicService clinic;
    private ScheduleService service;
    private ScheduleEntity schedule;

    @BeforeEach
    void setUp() {
        service = new ScheduleService(schedules, appointments, audit, clinic,
                org.mockito.Mockito.mock(com.dental.leave.mapper.DoctorLeaveMapper.class));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser(1L, "admin", "管理员", List.of("ADMIN"), null, "test"), null, List.of()));
        schedule = new ScheduleEntity();
        schedule.setId(SCHEDULE_ID);
        schedule.setDoctorId(10L);
        schedule.setDepartmentId(11L);
        schedule.setStatus("PUBLISHED");
        schedule.setStartTime(LocalDateTime.now(ZoneOffset.UTC).plusDays(3));
        schedule.setEndTime(schedule.getStartTime().plusHours(1));
        schedule.setTotalSlots(5);
        schedule.setBookedSlots(2);
        schedule.setCancelBeforeMinutes(120);
        when(schedules.lockSchedule(SCHEDULE_ID)).thenReturn(schedule);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void closeRetainsActiveAppointmentsAndOccupiedSlots() {
        when(schedules.updateStatus(eq(SCHEDULE_ID), eq("CLOSED"), any())).thenAnswer(call -> {
            schedule.setStatus("CLOSED");
            return 1;
        });

        var result = service.changeStatus(SCHEDULE_ID, "CLOSED");

        assertEquals("CLOSED", result.status());
        assertEquals(2, result.bookedSlots());
        assertEquals(3, result.remainingSlots());
        verify(appointments, never()).cancelActiveBySchedule(SCHEDULE_ID);
    }

    @Test
    void repeatedCloseIsIdempotentAndDoesNotCancelAppointments() {
        schedule.setStatus("CLOSED");

        var result = service.changeStatus(SCHEDULE_ID, "CLOSED");

        assertEquals("CLOSED", result.status());
        assertEquals(2, result.bookedSlots());
        verify(schedules, never()).updateStatus(eq(SCHEDULE_ID), any(), any());
        verify(appointments, never()).cancelActiveBySchedule(SCHEDULE_ID);
    }

    @Test
    void cancelPublishedScheduleCancelsActiveAppointments() {
        when(schedules.updateStatus(eq(SCHEDULE_ID), eq("CANCELLED"), any())).thenAnswer(call -> {
            schedule.setStatus("CANCELLED");
            return 1;
        });

        service.changeStatus(SCHEDULE_ID, "CANCELLED");

        verify(appointments).cancelActiveBySchedule(SCHEDULE_ID);
    }

    @Test
    void cancelClosedScheduleStillCancelsExistingAppointments() {
        schedule.setStatus("CLOSED");

        service.changeStatus(SCHEDULE_ID, "CANCELLED");

        verify(schedules).updateStatus(eq(SCHEDULE_ID), eq("CANCELLED"), any());
        verify(appointments).cancelActiveBySchedule(SCHEDULE_ID);
    }

    @Test
    void retryCancelledStatusDoesNotCancelOrReleaseTwice() {
        schedule.setStatus("CANCELLED");

        service.changeStatus(SCHEDULE_ID, "CANCELLED");

        verify(schedules, never()).updateStatus(eq(SCHEDULE_ID), any(), any());
        verify(appointments, never()).cancelActiveBySchedule(SCHEDULE_ID);
    }

    @Test
    void deletionOfClosedScheduleCancelsAndSoftDeletes() {
        schedule.setStatus("CLOSED");

        service.delete(SCHEDULE_ID);

        var order = org.mockito.Mockito.inOrder(schedules, appointments);
        order.verify(schedules).updateStatus(eq(SCHEDULE_ID), eq("CANCELLED"), any());
        order.verify(appointments).cancelActiveBySchedule(SCHEDULE_ID);
        order.verify(schedules).softDelete(eq(SCHEDULE_ID), any());
    }

    @Test
    void disablingDoctorCancelsEvenClosedSchedule() {
        schedule.setStatus("CLOSED");
        when(schedules.findScheduleIdsByDoctor(10L)).thenReturn(List.of(SCHEDULE_ID));

        service.closeByDoctor(10L);

        verify(schedules).updateStatus(eq(SCHEDULE_ID), eq("CANCELLED"), any());
        verify(appointments).cancelActiveBySchedule(SCHEDULE_ID);
    }

    @Test
    void disablingDepartmentCancelsExistingAppointments() {
        when(schedules.findScheduleIdsByDepartment(11L)).thenReturn(List.of(SCHEDULE_ID));

        service.closeByDepartment(11L);

        verify(schedules).updateStatus(eq(SCHEDULE_ID), eq("CANCELLED"), any());
        verify(appointments, times(1)).cancelActiveBySchedule(SCHEDULE_ID);
    }
}
