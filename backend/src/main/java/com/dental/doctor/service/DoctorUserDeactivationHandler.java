package com.dental.doctor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dental.audit.service.OperationLogService;
import com.dental.common.TimeUtil;
import com.dental.doctor.entity.DoctorEntity;
import com.dental.doctor.mapper.DoctorMapper;
import com.dental.schedule.service.ScheduleService;
import com.dental.user.service.UserDeactivationHandler;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 医生账号停用、删除或失去角色时停诊并解除档案关联。 */
@Service
public class DoctorUserDeactivationHandler implements UserDeactivationHandler {

    private final DoctorMapper doctorMapper;
    private final ScheduleService scheduleService;
    private final OperationLogService operationLogService;

    public DoctorUserDeactivationHandler(DoctorMapper doctorMapper, ScheduleService scheduleService,
                                         OperationLogService operationLogService) {
        this.doctorMapper = doctorMapper;
        this.scheduleService = scheduleService;
        this.operationLogService = operationLogService;
    }

    @Override
    @Transactional
    public void beforeDeactivate(Long userId, Set<String> currentRoles) {
        DoctorEntity linked = doctorMapper.selectOne(new LambdaQueryWrapper<DoctorEntity>()
                .eq(DoctorEntity::getUserId, userId));
        if (linked == null) {
            return;
        }
        DoctorEntity doctor = doctorMapper.lockById(linked.getId());
        if (doctor == null || !userId.equals(doctor.getUserId())) {
            return;
        }
        scheduleService.closeByDoctor(doctor.getId());
        doctorMapper.update(null, new LambdaUpdateWrapper<DoctorEntity>()
                .eq(DoctorEntity::getId, doctor.getId())
                .set(DoctorEntity::getUserId, null)
                .set(DoctorEntity::getStatus, 0)
                .set(DoctorEntity::getUpdatedAt, TimeUtil.nowUtc()));
        operationLogService.record("DISABLE", "DOCTOR", doctor.getId().toString(), "医生账号停用，关联档案已停诊");
    }
}
