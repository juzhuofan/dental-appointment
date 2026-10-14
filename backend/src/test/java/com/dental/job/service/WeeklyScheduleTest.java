package com.dental.job.service;

import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.common.TimeUtil;
import com.dental.job.mapper.WeeklyScheduleMapper;
import com.dental.leave.mapper.DoctorLeaveMapper;
import com.dental.schedule.entity.ScheduleEntity;
import com.dental.schedule.mapper.ScheduleMapper;
import com.dental.schedule.vo.DoctorAssignmentVO;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WeeklyScheduleTest {
    ScheduleMapper schedules=mock(ScheduleMapper.class);
    WeeklyScheduleMapper weekly=mock(WeeklyScheduleMapper.class);
    DoctorLeaveMapper leaves=mock(DoctorLeaveMapper.class);
    OperationLogService audit=mock(OperationLogService.class);
    DoctorWeekGenerator generator=new DoctorWeekGenerator(schedules,weekly,leaves,audit);
    LocalDate target=WeeklyScheduleDates.mondayOf(TimeUtil.clinicToday()).plusWeeks(2);
    ScheduleEntity source;

    @BeforeEach void setup() {
        var doctor=new DoctorAssignmentVO();
        doctor.setDoctorId(10L);doctor.setDepartmentId(20L);doctor.setDoctorStatus(1);
        when(schedules.findDoctor(10)).thenReturn(doctor);
        when(schedules.lockDepartment(20)).thenReturn(1);
        when(schedules.lockDoctor(10)).thenReturn(doctor);
        source=new ScheduleEntity();source.setId(30L);source.setDoctorId(10L);
        source.setStartTime(WeeklyScheduleDates.utcStart(target.minusWeeks(1)).plusHours(9));
        source.setEndTime(source.getStartTime().plusHours(3));source.setTotalSlots(8);
        source.setBookedSlots(7);source.setCancelBeforeMinutes(120);source.setStatus("CLOSED");
        when(weekly.findSourceSchedules(eq(10L),any(),any())).thenReturn(List.of(source));
        when(weekly.findLeaveSeeds(eq(10L),any(),any())).thenReturn(List.of());
        when(schedules.insert(any(ScheduleEntity.class))).thenAnswer(call->{
            ((ScheduleEntity)call.getArgument(0)).setId(40L);return 1;
        });
    }

    @Test void sundayAtChinaMidnightMeansNextMondayAcrossYear() {
        assertEquals(LocalDate.of(2027,1,4),WeeklyScheduleDates.targetForTrigger(Instant.parse("2027-01-02T16:00:00Z")));
    }
    @Test void mondayMisfireStillTargetsThisMonday() {
        assertEquals(LocalDate.of(2026,10,19),WeeklyScheduleDates.targetForTrigger(Instant.parse("2026-10-19T03:00:00Z")));
    }
    @Test void beforeSundayBoundaryStaysInPreviousCycle() {
        assertEquals(LocalDate.of(2026,10,12),WeeklyScheduleDates.targetForTrigger(Instant.parse("2026-10-17T15:59:59Z")));
    }
    @Test void utcWeekBoundaryIsPreviousDayAtSixteen() {
        assertEquals(LocalDateTime.of(2026,10,18,16,0),WeeklyScheduleDates.utcStart(LocalDate.of(2026,10,19)));
    }
    @Test void copiesCapacityAndCutoffButNeverOccupiedSlotsOrClosedState() {
        var result=generator.generate(10,target);assertEquals(1,result.created());
        var saved=ArgumentCaptor.forClass(ScheduleEntity.class);verify(schedules).insert(saved.capture());
        var row=saved.getValue();assertEquals(8,row.getTotalSlots());assertEquals(0,row.getBookedSlots());
        assertEquals("PUBLISHED",row.getStatus());assertEquals(120,row.getCancelBeforeMinutes());
        assertEquals(0L,row.getCreatedBy());assertEquals(20L,row.getDepartmentId());
        assertEquals(source.getStartTime().plusWeeks(1),row.getStartTime());
        var order=inOrder(schedules,leaves);order.verify(schedules).findDoctor(10);
        order.verify(schedules).lockDepartment(20);order.verify(schedules).lockDoctor(10);
        order.verify(leaves).countApprovedOverlaps(eq(10L),any(),any());
    }
    @Test void approvedLeavePersistsNominalShiftWithoutCreatingBookableSchedule() {
        when(leaves.countApprovedOverlaps(eq(10L),any(),any())).thenReturn(1L);
        assertEquals(1,generator.generate(10,target).leave());verify(schedules,never()).insert(any(ScheduleEntity.class));
        var item=ArgumentCaptor.forClass(GenerationItem.class);verify(weekly).insertItem(item.capture());
        assertEquals("LEAVE",item.getValue().outcome());assertNull(item.getValue().scheduleId());
    }
    @Test void generationLedgerPreventsRecreatingDeletedGeneratedShift() {
        when(weekly.countGenerated(eq(10L),any(),any())).thenReturn(1L);
        assertEquals(1,generator.generate(10,target).existing());verify(schedules,never()).insert(any(ScheduleEntity.class));
        verify(weekly,never()).insertItem(any());
    }
    @Test void anyExistingManualOverlapIsPreservedAndRecorded() {
        when(schedules.countOverlaps(eq(10L),any(),any(),isNull())).thenReturn(1L);
        assertEquals(1,generator.generate(10,target).existing());verify(schedules,never()).insert(any(ScheduleEntity.class));
        var item=ArgumentCaptor.forClass(GenerationItem.class);verify(weekly).insertItem(item.capture());
        assertEquals("EXISTING",item.getValue().outcome());
    }
    @Test void historicalWeekKeepsWeekdayAndTime() {
        when(weekly.findSourceSchedules(eq(10L),any(),any())).thenReturn(List.of(),List.of(source));
        source.setStartTime(source.getStartTime().minusWeeks(3));source.setEndTime(source.getEndTime().minusWeeks(3));
        when(weekly.findMostRecentSourceStart(eq(10L),any())).thenReturn(source.getStartTime());
        var result=generator.generate(10,target);assertTrue(result.fallback());assertEquals(1,result.created());
        var row=ArgumentCaptor.forClass(ScheduleEntity.class);verify(schedules).insert(row.capture());
        assertEquals(source.getStartTime().plusWeeks(4),row.getValue().getStartTime());
    }
    @Test void nominalLeaveSeedRestoresShiftTheFollowingWeek() {
        when(weekly.findSourceSchedules(eq(10L),any(),any())).thenReturn(List.of());
        source.setId(null);when(weekly.findLeaveSeeds(eq(10L),any(),any())).thenReturn(List.of(source));
        assertEquals(1,generator.generate(10,target).created());
        verify(weekly,never()).findMostRecentSourceStart(anyLong(),any());
    }
    @Test void noSourceIsReportedWithoutGuessingHours() {
        when(weekly.findSourceSchedules(eq(10L),any(),any())).thenReturn(List.of());
        assertTrue(generator.generate(10,target).noSource());verify(schedules,never()).insert(any(ScheduleEntity.class));
    }
    @Test void disabledDepartmentIsSkippedAfterLock() {
        when(schedules.lockDepartment(20)).thenReturn(0);
        assertTrue(generator.generate(10,target).inactive());verify(weekly,never()).findSourceSchedules(anyLong(),any(),any());
    }
    @Test void movedDoctorIsSkippedInsteadOfUsingOldDepartmentLock() {
        var moved=new DoctorAssignmentVO();moved.setDepartmentId(99L);moved.setDoctorStatus(1);
        when(schedules.lockDoctor(10)).thenReturn(moved);assertTrue(generator.generate(10,target).inactive());
    }
    @Test void elapsedBookingWindowIsSkipped() {
        source.setCancelBeforeMinutes(10080);
        LocalDate thisMonday=WeeklyScheduleDates.mondayOf(TimeUtil.clinicToday());
        source.setStartTime(WeeklyScheduleDates.utcStart(thisMonday.minusWeeks(1)).plusHours(9));
        source.setEndTime(source.getStartTime().plusHours(3));
        assertEquals(1,generator.generate(10,thisMonday).expired());verify(schedules,never()).insert(any(ScheduleEntity.class));
    }
    @Test void invalidTargetIsRejectedBeforeAnyQuery() {
        WeeklyScheduleService service=new WeeklyScheduleService(weekly,generator);
        assertThrows(BusinessException.class,()->service.generate(target.plusDays(1)));
        assertThrows(BusinessException.class,()->service.generate(target.plusWeeks(9)));
        verify(weekly,never()).findActiveDoctorIds();
    }
    @Test void oneDoctorFailureDoesNotPreventOthersAndRemainsVisible() {
        var worker=mock(DoctorWeekGenerator.class);when(weekly.findActiveDoctorIds()).thenReturn(List.of(10L,11L));
        when(worker.generate(10,target)).thenThrow(new IllegalStateException("database unavailable"));
        when(worker.generate(11,target)).thenReturn(new DoctorWeekGenerator.Result(11,2,1,0,0,false,false,false));
        var result=new WeeklyScheduleService(weekly,worker).generate(target);
        assertEquals(2,result.created());assertEquals(List.of(10L),result.failedDoctorIds());assertFalse(result.successful());
    }
}
