package com.dental.job;

import com.dental.job.service.WeeklyScheduleDates;
import com.dental.job.service.WeeklyScheduleService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.LocalDate;

@Component
public class WeeklyDoctorScheduleJob {
    private final WeeklyScheduleService schedules;
    public WeeklyDoctorScheduleJob(WeeklyScheduleService schedules) { this.schedules=schedules; }

    @XxlJob("weeklyDoctorScheduleJob")
    public void execute() {
        try {
            String parameter=XxlJobHelper.getJobParam();
            long triggerMillis=XxlJobHelper.getLogDateTime();
            LocalDate monday=parameter==null || parameter.isBlank()
                    ? WeeklyScheduleDates.targetForTrigger(triggerMillis>0?Instant.ofEpochMilli(triggerMillis):Instant.now())
                    : LocalDate.parse(parameter.trim());
            var report=schedules.generate(monday);
            String summary="目标周="+report.targetMonday()+"，新增="+report.created()+"，请假跳过="+report.leave()
                    +"，已有="+report.existing()+"，过期="+report.expired()+"，沿用历史周医生="+report.fallbackDoctorIds()
                    +"，无来源医生="+report.noSourceDoctorIds()+"，失败医生="+report.failedDoctorIds();
            XxlJobHelper.log(summary);
            if (report.successful()) XxlJobHelper.handleSuccess(summary);
            else XxlJobHelper.handleFail(summary+"；无来源医生需先维护首周排班，再重试本任务");
        } catch (Exception error) {
            XxlJobHelper.log(error);
            XxlJobHelper.handleFail("排班生成失败："+error.getMessage());
        }
    }
}
