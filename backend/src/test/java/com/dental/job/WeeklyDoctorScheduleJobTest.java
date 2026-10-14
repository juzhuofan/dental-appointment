package com.dental.job;

import com.dental.common.TimeUtil;
import com.dental.job.service.WeeklyScheduleDates;
import com.dental.job.service.WeeklyScheduleService;
import com.xxl.job.core.context.XxlJobContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WeeklyDoctorScheduleJobTest {
    WeeklyScheduleService schedules=mock(WeeklyScheduleService.class);
    WeeklyDoctorScheduleJob job=new WeeklyDoctorScheduleJob(schedules);
    @AfterEach void clear() { XxlJobContext.setXxlJobContext(null); }
    XxlJobContext context(String parameter,Instant trigger) {
        var context=new XxlJobContext(1,parameter,1,trigger.toEpochMilli(),"",0,1);
        XxlJobContext.setXxlJobContext(context);return context;
    }
    WeeklyScheduleService.Report report(LocalDate target,List<Long> noSource,List<Long> failed) {
        return new WeeklyScheduleService.Report(target,2,1,3,0,noSource,List.of(),failed);
    }
    @Test void annotatedXxlHandlerUsesOriginalChinaTriggerDate() throws Exception {
        var method=WeeklyDoctorScheduleJob.class.getMethod("execute");
        assertEquals("weeklyDoctorScheduleJob",method.getAnnotation(com.xxl.job.core.handler.annotation.XxlJob.class).value());
        LocalDate monday=LocalDate.of(2026,10,19);
        var context=context("",Instant.parse("2026-10-17T16:00:00Z"));
        when(schedules.generate(monday)).thenReturn(report(monday,List.of(),List.of()));
        job.execute();verify(schedules).generate(monday);assertEquals(200,context.getHandleCode());
    }
    @Test void explicitMondaySupportsManualCatchup() {
        LocalDate monday=WeeklyScheduleDates.mondayOf(TimeUtil.clinicToday()).plusWeeks(1);
        var context=context(monday.toString(),Instant.now());
        when(schedules.generate(monday)).thenReturn(report(monday,List.of(),List.of()));
        job.execute();verify(schedules).generate(monday);assertEquals(200,context.getHandleCode());
    }
    @Test void missingSourceRemainsFailedAndVisibleInAdminLog() {
        var context=context("2026-10-19",Instant.now());
        when(schedules.generate(any())).thenReturn(report(LocalDate.of(2026,10,19),List.of(10L),List.of()));
        job.execute();assertEquals(500,context.getHandleCode());assertTrue(context.getHandleMsg().contains("无来源医生=[10]"));
    }
    @Test void failedDoctorDoesNotReportSuccess() {
        var context=context("2026-10-19",Instant.now());
        when(schedules.generate(any())).thenReturn(report(LocalDate.of(2026,10,19),List.of(),List.of(20L)));
        job.execute();assertEquals(500,context.getHandleCode());assertTrue(context.getHandleMsg().contains("失败医生=[20]"));
    }
    @Test void invalidParameterFailsBeforeWriting() {
        var context=context("2026-10-19,2026-10-26",Instant.now());
        job.execute();assertEquals(500,context.getHandleCode());verifyNoInteractions(schedules);
    }
}
