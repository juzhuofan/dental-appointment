package com.dental.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.schedule.dto.ScheduleFilterDTO;
import com.dental.schedule.entity.ScheduleEntity;
import com.dental.schedule.vo.DoctorAssignmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ScheduleMapper extends BaseMapper<ScheduleEntity> {

    DoctorAssignmentVO findDoctor(@Param("doctorId") long doctorId);

    Integer lockDepartment(@Param("departmentId") long departmentId);

    Integer findDepartmentStatus(@Param("departmentId") long departmentId);

    DoctorAssignmentVO lockDoctor(@Param("doctorId") long doctorId);

    ScheduleEntity lockSchedule(@Param("id") long id);

    ScheduleEntity findPublicSchedule(@Param("id") long id, @Param("now") LocalDateTime now);

    List<ScheduleEntity> findPage(@Param("filter") ScheduleFilterDTO filter,
                                  @Param("publicOnly") boolean publicOnly,
                                  @Param("doctorScope") Long doctorScope,
                                  @Param("fromUtc") LocalDateTime fromUtc,
                                  @Param("toUtc") LocalDateTime toUtc,
                                  @Param("now") LocalDateTime now,
                                  @Param("offset") long offset,
                                  @Param("limit") int limit);

    long countPage(@Param("filter") ScheduleFilterDTO filter,
                   @Param("publicOnly") boolean publicOnly,
                   @Param("doctorScope") Long doctorScope,
                   @Param("fromUtc") LocalDateTime fromUtc,
                   @Param("toUtc") LocalDateTime toUtc,
                   @Param("now") LocalDateTime now);

    long countOverlaps(@Param("doctorId") long doctorId,
                       @Param("start") LocalDateTime start,
                       @Param("end") LocalDateTime end,
                       @Param("excludeId") Long excludeId);

    List<Long> findScheduleIdsByDoctor(@Param("doctorId") long doctorId);

    List<Long> findScheduleIdsByDepartment(@Param("departmentId") long departmentId);

    int updateStatus(@Param("id") long id, @Param("status") String status,
                     @Param("now") LocalDateTime now);

    int softDelete(@Param("id") long id, @Param("now") LocalDateTime now);
}
