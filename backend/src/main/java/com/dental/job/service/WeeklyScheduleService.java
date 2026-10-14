package com.dental.job.service;

import com.dental.common.BusinessException;
import com.dental.common.TimeUtil;
import com.dental.job.mapper.WeeklyScheduleMapper;
import org.springframework.stereotype.Service;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class WeeklyScheduleService {
    private final WeeklyScheduleMapper weekly;
    private final DoctorWeekGenerator generator;
    public WeeklyScheduleService(WeeklyScheduleMapper weekly,DoctorWeekGenerator generator) {
        this.weekly=weekly; this.generator=generator;
    }
    public record Report(LocalDate targetMonday, int created, int leave, int existing, int expired,
                         List<Long> noSourceDoctorIds, List<Long> fallbackDoctorIds, List<Long> failedDoctorIds) {
        public boolean successful() { return failedDoctorIds.isEmpty() && noSourceDoctorIds.isEmpty(); }
    }
    public Report generate(LocalDate targetMonday) {
        LocalDate currentMonday=WeeklyScheduleDates.mondayOf(TimeUtil.clinicToday());
        if (targetMonday==null || targetMonday.getDayOfWeek()!=DayOfWeek.MONDAY
                || targetMonday.isBefore(currentMonday) || targetMonday.isAfter(currentMonday.plusWeeks(8))) {
            throw BusinessException.bad("目标日期必须是本周至未来八周内的星期一");
        }
        int created=0,leave=0,existing=0,expired=0;
        List<Long> noSource=new ArrayList<>(),fallback=new ArrayList<>(),failed=new ArrayList<>();
        for (long doctorId:weekly.findActiveDoctorIds()) {
            try {
                var r=generator.generate(doctorId,targetMonday);
                created+=r.created(); leave+=r.leave(); existing+=r.existing(); expired+=r.expired();
                if (r.noSource()) noSource.add(doctorId);
                if (r.fallback()) fallback.add(doctorId);
            } catch (RuntimeException error) {
                // Keep other doctors progressing; the failed transaction rolls back as a unit.
                org.slf4j.LoggerFactory.getLogger(getClass()).error("Weekly schedule failed: doctor={}, target={}",
                        doctorId,targetMonday,error);
                failed.add(doctorId);
            }
        }
        return new Report(targetMonday,created,leave,existing,expired,List.copyOf(noSource),
                List.copyOf(fallback),List.copyOf(failed));
    }
}
