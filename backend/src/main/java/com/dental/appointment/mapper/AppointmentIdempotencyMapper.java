package com.dental.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.appointment.entity.AppointmentIdempotencyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AppointmentIdempotencyMapper extends BaseMapper<AppointmentIdempotencyEntity> {

    int reserve(@Param("userId") long userId, @Param("requestKey") String requestKey,
                @Param("requestHash") String requestHash, @Param("now") LocalDateTime now);

    AppointmentIdempotencyEntity lock(@Param("userId") long userId,
                                      @Param("requestKey") String requestKey);

    int attachAppointment(@Param("id") long id, @Param("appointmentId") long appointmentId,
                          @Param("now") LocalDateTime now);
}
