package com.dental.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.user.entity.PatientProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 默认就诊人资料持久化。 */
@Mapper
public interface PatientProfileMapper extends BaseMapper<PatientProfile> {
    PatientProfile lockIncludingDeleted(@Param("userId") Long userId);

    int restore(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    long countActiveAppointments(@Param("profileId") Long profileId,
                                 @Param("userId") Long userId);
}
