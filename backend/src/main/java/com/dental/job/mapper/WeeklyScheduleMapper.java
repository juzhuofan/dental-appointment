package com.dental.job.mapper;

import com.dental.job.service.GenerationItem;
import com.dental.schedule.entity.ScheduleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WeeklyScheduleMapper {
    List<Long> findActiveDoctorIds();
    List<ScheduleEntity> findSourceSchedules(@Param("doctorId") long doctorId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    List<ScheduleEntity> findLeaveSeeds(@Param("doctorId") long doctorId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    LocalDateTime findMostRecentSourceStart(@Param("doctorId") long doctorId,
            @Param("before") LocalDateTime before);
    long countGenerated(@Param("doctorId") long doctorId, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
    int insertItem(@Param("item") GenerationItem item);
}
