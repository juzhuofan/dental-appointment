package com.dental.leave.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dental.appointment.service.AppointmentService;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.common.TimeUtil;
import com.dental.leave.dto.DoctorLeaveApplyDTO;
import com.dental.leave.dto.DoctorLeaveFilterDTO;
import com.dental.leave.dto.DoctorLeaveReviewDTO;
import com.dental.leave.entity.DoctorLeaveEntity;
import com.dental.leave.mapper.DoctorLeaveMapper;
import com.dental.schedule.entity.ScheduleEntity;
import com.dental.schedule.mapper.ScheduleMapper;
import com.dental.schedule.vo.DoctorAssignmentVO;
import com.dental.security.CurrentUser;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DoctorLeaveServiceTest {
    private final DoctorLeaveMapper leaves = mock(DoctorLeaveMapper.class);
    private final ScheduleMapper schedules = mock(ScheduleMapper.class);
    private final AppointmentService appointments = mock(AppointmentService.class);
    private final OperationLogService audit = mock(OperationLogService.class);
    private final DoctorLeaveService service = new DoctorLeaveService(leaves, schedules, appointments, audit);
    private DoctorLeaveEntity leave;

    @BeforeEach
    void setUp() {
        authenticate("ADMIN", null);
        leave = new DoctorLeaveEntity();
        leave.setId(8L);
        leave.setDoctorId(10L);
        leave.setDoctorName("张医生");
        leave.setDepartmentName("口腔科");
        leave.setStartTime(LocalDateTime.of(2099, 5, 4, 1, 0));
        leave.setEndTime(leave.getStartTime().plusHours(4));
        leave.setReason("事假");
        leave.setStatus("PENDING");
        leave.setDeleted(0);
        leave.setCreatedAt(LocalDateTime.of(2099, 5, 1, 0, 0));
    }

    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }

    @Test
    void applyingUsesAuthenticatedDoctorAndUtcAndOnlyCreatesPending() {
        authenticate("DOCTOR", 10L);
        stubDoctor();
        when(leaves.insert(any(DoctorLeaveEntity.class))).thenAnswer(call -> {
            DoctorLeaveEntity entity = call.getArgument(0);
            entity.setId(8L);
            return 1;
        });
        when(leaves.findDetail(8L)).thenReturn(leave);
        DoctorLeaveApplyDTO input = applyInput();
        input.setReason("  事假  ");

        var result = service.apply(input);

        ArgumentCaptor<DoctorLeaveEntity> entity = ArgumentCaptor.forClass(DoctorLeaveEntity.class);
        verify(leaves).insert(entity.capture());
        assertEquals(10L, entity.getValue().getDoctorId());
        assertEquals("PENDING", entity.getValue().getStatus());
        assertEquals("事假", entity.getValue().getReason());
        assertEquals(LocalDateTime.of(2099, 5, 4, 1, 0), entity.getValue().getStartTime());
        assertEquals(0, entity.getValue().getDeleted());
        assertEquals(ZoneOffset.ofHours(8), result.startTime().getOffset());
        assertEquals(9, result.startTime().getHour());
        var order = inOrder(schedules, leaves);
        order.verify(schedules).lockDepartment(11L);
        order.verify(schedules).lockDoctor(10L);
        order.verify(leaves).insert(any(DoctorLeaveEntity.class));
        verify(leaves, never()).lockOverlappingScheduleIds(anyLong(), any(), any());
        verifyNoInteractions(appointments);
        verify(audit).record(eq("DOCTOR_LEAVE_APPLY"), eq("DOCTOR_LEAVE"), eq("8"), anyString());
    }

    @Test
    void adminCannotApplyOnBehalfOfDoctor() {
        assertEquals(403, assertThrows(BusinessException.class, () -> service.apply(applyInput())).getHttpStatus());
        verifyNoInteractions(leaves, schedules, appointments);
    }

    @Test
    void unboundDoctorGetsFriendlyForbiddenInsteadOfNullScope() {
        authenticate("DOCTOR", null);
        var error = assertThrows(BusinessException.class, () -> service.apply(applyInput()));
        assertEquals(403, error.getHttpStatus());
        assertTrue(error.getMessage().contains("关联医生"));
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.list(new DoctorLeaveFilterDTO())).getHttpStatus());
        verifyNoInteractions(leaves, schedules);
    }

    @Test
    void rejectsInvalidTimeAndReasonBeforeWriting() {
        authenticate("DOCTOR", 10L);
        var input = applyInput();
        input.setEndTime(input.getStartTime());
        assertEquals(400, assertThrows(BusinessException.class, () -> service.apply(input)).getHttpStatus());
        input.setStartTime(OffsetDateTime.now().minusMinutes(1));
        input.setEndTime(OffsetDateTime.now().plusDays(1));
        assertEquals(400, assertThrows(BusinessException.class, () -> service.apply(input)).getHttpStatus());
        input.setStartTime(applyInput().getStartTime());
        input.setEndTime(applyInput().getEndTime());
        input.setReason(" ");
        assertEquals(400, assertThrows(BusinessException.class, () -> service.apply(input)).getHttpStatus());
        input.setReason("字".repeat(501));
        assertEquals(400, assertThrows(BusinessException.class, () -> service.apply(input)).getHttpStatus());
        verifyNoInteractions(leaves, schedules);
    }

    @Test
    void inactiveDoctorOrDepartmentCannotApply() {
        authenticate("DOCTOR", 10L);
        stubDoctor();
        when(schedules.lockDepartment(11L)).thenReturn(0);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.apply(applyInput())).getHttpStatus());
        verify(leaves, never()).insert(any(DoctorLeaveEntity.class));
    }

    @Test
    void doctorListIsScopedAndCannotReadOtherDoctors() {
        authenticate("DOCTOR", 10L);
        when(leaves.countPage(10L, null)).thenReturn(1L);
        when(leaves.findPage(10L, null, 0L, 20)).thenReturn(List.of(leave));
        assertEquals(1, service.list(new DoctorLeaveFilterDTO()).records().size());
        verify(leaves).countPage(10L, null);
        var filter = new DoctorLeaveFilterDTO();
        filter.setDoctorId(99L);
        assertEquals(403, assertThrows(BusinessException.class, () -> service.list(filter)).getHttpStatus());
        verify(leaves, never()).countPage(eq(99L), any());
    }

    @Test
    void adminCanFilterAllDoctorsAndPaginationIsBounded() {
        var filter = new DoctorLeaveFilterDTO();
        filter.setPage(3);
        filter.setSize(10);
        filter.setStatus(" APPROVED ");
        when(leaves.countPage(null, "APPROVED")).thenReturn(30L);
        when(leaves.findPage(null, "APPROVED", 20L, 10)).thenReturn(List.of(leave));
        assertEquals(30, service.list(filter).total());
        verify(leaves).findPage(null, "APPROVED", 20L, 10);
        filter.setSize(101);
        assertEquals(400, assertThrows(BusinessException.class, () -> service.list(filter)).getHttpStatus());
    }

    @Test
    void patientCannotListOrReview() {
        authenticate("PATIENT", null);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.list(new DoctorLeaveFilterDTO())).getHttpStatus());
        assertEquals(403, assertThrows(BusinessException.class, () -> service.review(8L, reviewInput(true, false))).getHttpStatus());
        verifyNoInteractions(leaves, schedules);
    }

    @Test
    void occupiedSchedulesRequireExplicitConfirmationAndLeaveEverythingUntouched() {
        stubReview();
        when(leaves.lockOverlappingScheduleIds(10L, leave.getStartTime(), leave.getEndTime()))
                .thenReturn(List.of(101L, 102L));
        when(leaves.countActiveAppointments(List.of(101L, 102L))).thenReturn(3L);

        var error = assertThrows(BusinessException.class, () -> service.review(8L, reviewInput(true, false)));

        assertEquals(409, error.getHttpStatus());
        assertEquals("LEAVE_APPOINTMENT_CONFIRMATION_REQUIRED", error.getCode());
        assertTrue(error.getMessage().contains("3 个未完成预约"));
        verify(leaves, never()).reviewPending(anyLong(), anyString(), anyLong(), any(), any());
        verify(leaves, never()).linkCancelledSchedule(anyLong(), anyLong());
        verify(schedules, never()).updateStatus(anyLong(), anyString(), any());
        verifyNoInteractions(appointments, audit);
    }

    @Test
    void confirmedApprovalLocksThenLinksCancelsAndReleasesAllAffectedSchedules() {
        stubReview();
        when(leaves.lockOverlappingScheduleIds(10L, leave.getStartTime(), leave.getEndTime()))
                .thenReturn(List.of(101L, 102L, 103L));
        when(leaves.countActiveAppointments(List.of(101L, 102L, 103L))).thenReturn(2L);
        when(schedules.lockSchedule(101L)).thenReturn(schedule(101L, "DRAFT"));
        when(schedules.lockSchedule(102L)).thenReturn(schedule(102L, "PUBLISHED"));
        when(schedules.lockSchedule(103L)).thenReturn(schedule(103L, "CLOSED"));
        when(schedules.updateStatus(anyLong(), eq("CANCELLED"), any())).thenReturn(1);
        when(leaves.reviewPending(eq(8L), eq("APPROVED"), eq(1L), isNull(), any())).thenAnswer(call -> {
            leave.setStatus("APPROVED");
            leave.setReviewedBy(1L);
            return 1;
        });

        assertEquals("APPROVED", service.review(8L, reviewInput(true, true)).status());

        var order = inOrder(schedules, leaves, appointments);
        order.verify(schedules).lockDepartment(11L);
        order.verify(schedules).lockDoctor(10L);
        order.verify(leaves).lockLeave(8L);
        order.verify(leaves).lockOverlappingScheduleIds(10L, leave.getStartTime(), leave.getEndTime());
        order.verify(leaves).countActiveAppointments(List.of(101L, 102L, 103L));
        order.verify(leaves).reviewPending(eq(8L), eq("APPROVED"), eq(1L), isNull(), any());
        for (Long id : List.of(101L, 102L, 103L)) {
            order.verify(schedules).lockSchedule(id);
            if (id != 101L) order.verify(leaves).linkCancelledSchedule(8L, id);
            order.verify(schedules).updateStatus(eq(id), eq("CANCELLED"), any());
            order.verify(appointments).cancelActiveBySchedule(id);
        }
        verify(leaves, never()).linkCancelledSchedule(8L, 101L);
    }

    @Test
    void approvalWithoutExistingSchedulesSupportsFutureGeneratorSkip() {
        stubReview();
        when(leaves.lockOverlappingScheduleIds(10L, leave.getStartTime(), leave.getEndTime())).thenReturn(List.of());
        when(leaves.reviewPending(eq(8L), eq("APPROVED"), eq(1L), isNull(), any())).thenReturn(1);

        service.review(8L, reviewInput(true, false));

        verify(leaves, never()).countActiveAppointments(anyList());
        verifyNoInteractions(appointments);
        verify(leaves).reviewPending(eq(8L), eq("APPROVED"), eq(1L), isNull(), any());
    }

    @Test
    void rejectionRequiresReasonAndDoesNotTouchAnySchedule() {
        var invalid = reviewInput(false, false);
        assertEquals(400, assertThrows(BusinessException.class, () -> service.review(8L, invalid)).getHttpStatus());
        stubReview();
        invalid.setRemark("  排班已协调，暂不批准  ");
        when(leaves.reviewPending(eq(8L), eq("REJECTED"), eq(1L), eq("排班已协调，暂不批准"), any())).thenReturn(1);
        service.review(8L, invalid);
        verify(leaves, never()).lockOverlappingScheduleIds(anyLong(), any(), any());
        verify(schedules, never()).lockSchedule(anyLong());
        verifyNoInteractions(appointments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED", "WITHDRAWN"})
    void finalStatesCannotBeReviewedAgain(String status) {
        stubReview();
        leave.setStatus(status);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.review(8L, reviewInput(true, true))).getHttpStatus());
        verify(leaves, never()).lockOverlappingScheduleIds(anyLong(), any(), any());
        verifyNoInteractions(appointments, audit);
    }

    @Test
    void doctorCannotApproveOwnLeave() {
        authenticate("DOCTOR", 10L);
        assertEquals(403, assertThrows(BusinessException.class, () -> service.review(8L, reviewInput(true, true))).getHttpStatus());
        verifyNoInteractions(leaves, schedules);
    }

    @Test
    void withdrawOwnPendingUsesSameDoctorMutexAndNeverCancelsBookings() {
        authenticate("DOCTOR", 10L);
        stubReview();
        when(leaves.withdrawPending(eq(8L), eq(10L), any())).thenAnswer(call -> {
            leave.setStatus("WITHDRAWN");
            return 1;
        });
        assertEquals("WITHDRAWN", service.withdraw(8L).status());
        var order = inOrder(schedules, leaves);
        order.verify(schedules).lockDepartment(11L);
        order.verify(schedules).lockDoctor(10L);
        order.verify(leaves).lockLeave(8L);
        order.verify(leaves).withdrawPending(eq(8L), eq(10L), any());
        verifyNoInteractions(appointments);
    }

    @Test
    void cannotWithdrawAnotherDoctorOrApprovedLeave() {
        authenticate("DOCTOR", 99L);
        when(leaves.findDetail(8L)).thenReturn(leave);
        assertEquals(403, assertThrows(BusinessException.class, () -> service.withdraw(8L)).getHttpStatus());
        verifyNoInteractions(schedules);
        authenticate("DOCTOR", 10L);
        stubReview();
        leave.setStatus("APPROVED");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.withdraw(8L)).getHttpStatus());
        verify(leaves, never()).withdrawPending(anyLong(), anyLong(), any());
    }

    @Test
    void doctorDepartmentChangeDuringLockWaitStopsMutation() {
        stubReview();
        var moved = doctor();
        moved.setDepartmentId(22L);
        when(schedules.lockDoctor(10L)).thenReturn(moved);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.review(8L, reviewInput(true, true))).getHttpStatus());
        verify(leaves, never()).lockLeave(anyLong());
        verifyNoInteractions(appointments, audit);
    }

    private void stubReview() {
        stubDoctor();
        when(leaves.findDetail(8L)).thenReturn(leave);
        when(leaves.lockLeave(8L)).thenReturn(leave);
    }

    private void stubDoctor() {
        when(schedules.findDoctor(10L)).thenReturn(doctor());
        when(schedules.lockDepartment(11L)).thenReturn(1);
        when(schedules.lockDoctor(10L)).thenReturn(doctor());
    }

    private static DoctorAssignmentVO doctor() {
        var result = new DoctorAssignmentVO();
        result.setDoctorId(10L);
        result.setDepartmentId(11L);
        result.setDoctorStatus(1);
        return result;
    }

    private static ScheduleEntity schedule(long id, String status) {
        var result = new ScheduleEntity();
        result.setId(id);
        result.setDoctorId(10L);
        result.setStatus(status);
        return result;
    }

    private static DoctorLeaveApplyDTO applyInput() {
        var result = new DoctorLeaveApplyDTO();
        result.setStartTime(OffsetDateTime.parse("2099-05-04T09:00:00+08:00"));
        result.setEndTime(OffsetDateTime.parse("2099-05-04T13:00:00+08:00"));
        result.setReason("事假");
        return result;
    }

    private static DoctorLeaveReviewDTO reviewInput(boolean approved, boolean confirm) {
        var result = new DoctorLeaveReviewDTO();
        result.setApproved(approved);
        result.setConfirmCancelAppointments(confirm);
        return result;
    }

    private static void authenticate(String role, Long doctorId) {
        var user = new CurrentUser(1L, "test", "测试", List.of(role), doctorId, "unit-token");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
