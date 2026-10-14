package com.dental.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.leave.entity.DoctorLeaveEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DoctorLeaveMapper extends BaseMapper<DoctorLeaveEntity> {
    DoctorLeaveEntity findDetail(@Param("id") long id);

    DoctorLeaveEntity lockLeave(@Param("id") long id);

    List<DoctorLeaveEntity> findPage(@Param("doctorId") Long doctorId, @Param("status") String status,
                                    @Param("offset") long offset, @Param("limit") int limit);

    long countPage(@Param("doctorId") Long doctorId, @Param("status") String status);

    /** 当前读，调用方必须已经按科室、医生顺序持有医生锁。 */
    long countApprovedOverlaps(@Param("doctorId") long doctorId,
                               @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 审批已锁住医生和申请，再按 id 顺序锁定受影响排班。 */
    List<Long> lockOverlappingScheduleIds(@Param("doctorId") long doctorId,
                                          @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countActiveAppointments(@Param("scheduleIds") List<Long> scheduleIds);

    int linkCancelledSchedule(@Param("leaveId") long leaveId, @Param("scheduleId") long scheduleId);

    int reviewPending(@Param("id") long id, @Param("status") String status,
                      @Param("reviewedBy") long reviewedBy, @Param("remark") String remark,
                      @Param("now") LocalDateTime now);

    int withdrawPending(@Param("id") long id, @Param("doctorId") long doctorId,
                        @Param("now") LocalDateTime now);
}
