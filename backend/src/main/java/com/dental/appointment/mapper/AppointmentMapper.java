package com.dental.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.appointment.dto.AppointmentFilterDTO;
import com.dental.appointment.entity.AppointmentEntity;
import com.dental.appointment.vo.BookableScheduleVO;
import com.dental.appointment.vo.DailyTrendVO;
import com.dental.appointment.vo.PatientSnapshotVO;
import com.dental.appointment.vo.StatusCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppointmentMapper extends BaseMapper<AppointmentEntity> {

    Integer lockActiveUser(@Param("userId") long userId);

    PatientSnapshotVO findPatientProfile(@Param("profileId") long profileId,
                                         @Param("userId") long userId);

    BookableScheduleVO lockScheduleForBooking(@Param("scheduleId") long scheduleId);

    Long lockScheduleId(@Param("scheduleId") long scheduleId);

    int reserveSlot(@Param("scheduleId") long scheduleId, @Param("now") LocalDateTime now);

    int releaseSlot(@Param("scheduleId") long scheduleId, @Param("now") LocalDateTime now);

    AppointmentEntity findDetail(@Param("id") long id);

    AppointmentEntity lockAppointment(@Param("id") long id);

    List<AppointmentEntity> findPage(@Param("filter") AppointmentFilterDTO filter,
                                     @Param("patientScope") Long patientScope,
                                     @Param("doctorScope") Long doctorScope,
                                     @Param("fromUtc") LocalDateTime fromUtc,
                                     @Param("toUtc") LocalDateTime toUtc,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    long countPage(@Param("filter") AppointmentFilterDTO filter,
                   @Param("patientScope") Long patientScope,
                   @Param("doctorScope") Long doctorScope,
                   @Param("fromUtc") LocalDateTime fromUtc,
                   @Param("toUtc") LocalDateTime toUtc);

    List<AppointmentEntity> findActiveByScheduleForUpdate(@Param("scheduleId") long scheduleId,
                                                           @Param("patientUserId") Long patientUserId);

    List<Long> findActiveScheduleIdsByDepartment(@Param("departmentId") long departmentId);

    List<Long> findActiveScheduleIdsByDoctor(@Param("doctorId") long doctorId);

    List<Long> findActiveScheduleIdsByPatient(@Param("patientUserId") long patientUserId);

    Long findDoctorIdByUserId(@Param("userId") long userId);

    int transition(@Param("id") long id,
                   @Param("fromStatus") String fromStatus,
                   @Param("toStatus") String toStatus,
                   @Param("activeKey") String activeKey,
                   @Param("cancelReason") String cancelReason,
                   @Param("cancelledAt") LocalDateTime cancelledAt,
                   @Param("handledBy") Long handledBy,
                   @Param("handledAt") LocalDateTime handledAt,
                   @Param("now") LocalDateTime now);

    int softDelete(@Param("id") long id, @Param("now") LocalDateTime now);

    long countToday(@Param("doctorScope") Long doctorScope,
                    @Param("fromUtc") LocalDateTime fromUtc,
                    @Param("toUtc") LocalDateTime toUtc);

    long countPending(@Param("doctorScope") Long doctorScope);

    long countPatients(@Param("doctorScope") Long doctorScope);

    long countDoctors(@Param("doctorScope") Long doctorScope);

    List<StatusCountVO> statusCounts(@Param("doctorScope") Long doctorScope);

    List<DailyTrendVO> dailyTrend(@Param("doctorScope") Long doctorScope,
                                  @Param("fromUtc") LocalDateTime fromUtc);
}
