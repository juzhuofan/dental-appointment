package com.dental.job.service;

import com.dental.audit.service.OperationLogService;
import com.dental.common.TimeUtil;
import com.dental.job.mapper.WeeklyScheduleMapper;
import com.dental.leave.mapper.DoctorLeaveMapper;
import com.dental.schedule.entity.ScheduleEntity;
import com.dental.schedule.mapper.ScheduleMapper;
import com.dental.schedule.vo.DoctorAssignmentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One doctor per transaction. The same department/doctor locks serialize manual edits and leave reviews. */
@Service
public class DoctorWeekGenerator {
    private final ScheduleMapper schedules;
    private final WeeklyScheduleMapper weekly;
    private final DoctorLeaveMapper leaves;
    private final OperationLogService audit;
    public DoctorWeekGenerator(ScheduleMapper schedules, WeeklyScheduleMapper weekly,
            DoctorLeaveMapper leaves, OperationLogService audit) {
        this.schedules=schedules; this.weekly=weekly; this.leaves=leaves; this.audit=audit;
    }

    public record Result(long doctorId, int created, int leave, int existing, int expired,
                         boolean noSource, boolean fallback, boolean inactive) { }

    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public Result generate(long doctorId, LocalDate targetMonday) {
        DoctorAssignmentVO initial=schedules.findDoctor(doctorId);
        if (initial==null) return inactive(doctorId);
        Integer departmentStatus=schedules.lockDepartment(initial.getDepartmentId());
        DoctorAssignmentVO doctor=schedules.lockDoctor(doctorId);
        if (doctor==null || !Objects.equals(doctor.getDepartmentId(),initial.getDepartmentId())
                || !Objects.equals(departmentStatus,1) || !Objects.equals(doctor.getDoctorStatus(),1)) {
            return inactive(doctorId);
        }
        LocalDate sourceMonday=targetMonday.minusWeeks(1);
        List<ScheduleEntity> sources=sources(doctorId,sourceMonday);
        boolean fallback=false;
        if (sources.isEmpty()) {
            LocalDateTime recent=weekly.findMostRecentSourceStart(doctorId,WeeklyScheduleDates.utcStart(sourceMonday));
            if (recent!=null) {
                sourceMonday=WeeklyScheduleDates.mondayOf(TimeUtil.toClinic(recent).toLocalDate());
                sources=sources(doctorId,sourceMonday);
                fallback=true;
            }
        }
        if (sources.isEmpty()) return new Result(doctorId,0,0,0,0,true,false,false);
        int created=0,onLeave=0,existing=0,expired=0;
        long days=ChronoUnit.DAYS.between(sourceMonday,targetMonday);
        for (ScheduleEntity source:sources) {
            LocalDateTime start=TimeUtil.toUtc(TimeUtil.toClinic(source.getStartTime()).plusDays(days));
            LocalDateTime end=TimeUtil.toUtc(TimeUtil.toClinic(source.getEndTime()).plusDays(days));
            if (weekly.countGenerated(doctorId,start,end)>0) { existing++; continue; }
            LocalDateTime now=TimeUtil.nowUtc();
            String outcome;
            Long newId=null;
            if (!start.minusMinutes(source.getCancelBeforeMinutes()).isAfter(now)) {
                outcome="EXPIRED"; expired++;
            } else if (leaves.countApprovedOverlaps(doctorId,start,end)>0) {
                outcome="LEAVE"; onLeave++;
            } else if (schedules.countOverlaps(doctorId,start,end,null)>0) {
                outcome="EXISTING"; existing++;
            } else {
                ScheduleEntity target=new ScheduleEntity();
                target.setDoctorId(doctorId); target.setDepartmentId(doctor.getDepartmentId());
                target.setStartTime(start); target.setEndTime(end); target.setTotalSlots(source.getTotalSlots());
                target.setBookedSlots(0); target.setStatus("PUBLISHED");
                target.setCancelBeforeMinutes(source.getCancelBeforeMinutes()); target.setVersion(0);
                target.setCreatedBy(0L); // Reserved system actor; no fabricated login identity.
                target.setDeleted(0); target.setCreatedAt(now); target.setUpdatedAt(now);
                schedules.insert(target); newId=target.getId(); outcome="CREATED"; created++;
            }
            weekly.insertItem(new GenerationItem(targetMonday,doctorId,sourceMonday,source.getId(),
                    start,end,source.getTotalSlots(),source.getCancelBeforeMinutes(),outcome,newId,now));
        }
        audit.recordAs(null,"自动排班任务","SYSTEM","SCHEDULE_WEEK_GENERATE","DOCTOR",
                String.valueOf(doctorId),"目标周="+targetMonday+"，来源周="+sourceMonday+"，新增="+created
                        +"，请假跳过="+onLeave+"，已有="+existing+"，过期="+expired);
        return new Result(doctorId,created,onLeave,existing,expired,false,fallback,false);
    }

    private List<ScheduleEntity> sources(long doctorId,LocalDate monday) {
        LocalDateTime from=WeeklyScheduleDates.utcStart(monday),to=WeeklyScheduleDates.utcStart(monday.plusWeeks(1));
        List<ScheduleEntity> rows=new ArrayList<>(weekly.findSourceSchedules(doctorId,from,to));
        rows.addAll(weekly.findLeaveSeeds(doctorId,from,to));
        rows.sort(java.util.Comparator.comparing(ScheduleEntity::getStartTime));
        return rows;
    }
    private static Result inactive(long doctorId) { return new Result(doctorId,0,0,0,0,false,false,true); }
}
