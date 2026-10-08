package com.dental.doctor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.TimeUtil;
import com.dental.department.service.DepartmentQueryService;
import com.dental.doctor.dto.DoctorQueryDTO;
import com.dental.doctor.dto.DoctorSaveDTO;
import com.dental.doctor.entity.DoctorEntity;
import com.dental.doctor.mapper.DoctorMapper;
import com.dental.doctor.vo.DoctorVO;
import com.dental.schedule.service.ScheduleService;
import com.dental.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 医生档案管理及公开查询。 */
@Service
public class DoctorService {

    private final DoctorMapper doctorMapper;
    private final DepartmentQueryService departmentQueryService;
    private final UserService userService;
    private final ScheduleService scheduleService;
    private final OperationLogService operationLogService;

    public DoctorService(DoctorMapper doctorMapper, DepartmentQueryService departmentQueryService,
                         UserService userService, ScheduleService scheduleService,
                         OperationLogService operationLogService) {
        this.doctorMapper = doctorMapper;
        this.departmentQueryService = departmentQueryService;
        this.userService = userService;
        this.scheduleService = scheduleService;
        this.operationLogService = operationLogService;
    }

    public PageResult<DoctorVO> list(DoctorQueryDTO query, boolean publicOnly) {
        checkPage(query.page(), query.size());
        LambdaQueryWrapper<DoctorEntity> wrapper = new LambdaQueryWrapper<>();
        if (publicOnly) {
            List<Long> enabledDepartments = departmentQueryService.enabledIds();
            if (enabledDepartments.isEmpty()
                    || (query.departmentId() != null && !enabledDepartments.contains(query.departmentId()))) {
                return new PageResult<>(List.of(), 0, query.page(), query.size());
            }
            wrapper.eq(DoctorEntity::getStatus, 1)
                    .in(DoctorEntity::getDepartmentId, enabledDepartments);
        } else if (query.status() != null) {
            wrapper.eq(DoctorEntity::getStatus, query.status());
        }
        if (query.departmentId() != null) {
            wrapper.eq(DoctorEntity::getDepartmentId, query.departmentId());
        }
        if (hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(condition -> condition.like(DoctorEntity::getName, keyword)
                    .or().like(DoctorEntity::getSpecialty, keyword));
        }
        wrapper.orderByAsc(DoctorEntity::getId);
        Page<DoctorEntity> result = doctorMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        Set<Long> departmentIds = result.getRecords().stream().map(DoctorEntity::getDepartmentId)
                .collect(Collectors.toSet());
        Map<Long, String> names = departmentQueryService.namesByIds(departmentIds);
        List<DoctorVO> records = result.getRecords().stream().map(doctor -> toVO(doctor, names)).toList();
        return new PageResult<>(records, result.getTotal(), query.page(), query.size());
    }

    public DoctorVO publicDetail(long id) {
        DoctorEntity doctor = doctorMapper.selectById(id);
        if (doctor == null || !Integer.valueOf(1).equals(doctor.getStatus())) {
            throw BusinessException.missing("医生不存在或已停用");
        }
        String departmentName = departmentQueryService.requireEnabled(doctor.getDepartmentId()).getName();
        return toVO(doctor, Map.of(doctor.getDepartmentId(), departmentName));
    }

    @Transactional
    public DoctorVO create(DoctorSaveDTO input) {
        departmentQueryService.lockEnabled(input.departmentId());
        validateUser(input.userId());
        DoctorEntity doctor = new DoctorEntity();
        copy(input, doctor);
        doctor.setDeleted(0);
        doctor.setCreatedAt(TimeUtil.nowUtc());
        doctor.setUpdatedAt(doctor.getCreatedAt());
        try {
            doctorMapper.insert(doctor);
        } catch (DuplicateKeyException duplicate) {
            throw BusinessException.conflict("医生账号已关联其他档案");
        }
        operationLogService.record("CREATE", "DOCTOR", doctor.getId().toString(), "新增医生档案");
        return toVO(doctor, departmentQueryService.namesByIds(Set.of(doctor.getDepartmentId())));
    }

    @Transactional
    public DoctorVO update(long id, DoctorSaveDTO input) {
        departmentQueryService.lockEnabled(input.departmentId());
        DoctorEntity doctor = requireLocked(id);
        if (!Objects.equals(doctor.getDepartmentId(), input.departmentId())
                && scheduleService.hasAnyScheduleForDoctor(id)) {
            throw BusinessException.conflict("该医生存在排班，请先处理排班后再更换科室");
        }
        validateUser(input.userId());
        if (Integer.valueOf(1).equals(doctor.getStatus()) && input.status() == 0) {
            scheduleService.closeByDoctor(id);
        }
        copy(input, doctor);
        doctor.setUpdatedAt(TimeUtil.nowUtc());
        try {
            doctorMapper.updateById(doctor);
        } catch (DuplicateKeyException duplicate) {
            throw BusinessException.conflict("医生账号已关联其他档案");
        }
        operationLogService.record("UPDATE", "DOCTOR", Long.toString(id), "更新医生档案");
        return toVO(doctor, departmentQueryService.namesByIds(Set.of(doctor.getDepartmentId())));
    }

    @Transactional
    public void delete(long id) {
        requireLocked(id);
        scheduleService.closeByDoctor(id);
        LambdaUpdateWrapper<DoctorEntity> wrapper = new LambdaUpdateWrapper<DoctorEntity>()
                .eq(DoctorEntity::getId, id)
                .set(DoctorEntity::getStatus, 0)
                .set(DoctorEntity::getDeleted, 1)
                .set(DoctorEntity::getUpdatedAt, TimeUtil.nowUtc());
        if (doctorMapper.update(null, wrapper) != 1) {
            throw BusinessException.conflict("医生状态已变化，请刷新后重试");
        }
        operationLogService.record("DELETE", "DOCTOR", Long.toString(id), "逻辑删除医生档案");
    }

    /** 科室已被行锁保护，在同一事务内锁医生并停用。 */
    @Transactional
    public void disableByDepartment(long departmentId) {
        List<DoctorEntity> doctors = doctorMapper.lockByDepartment(departmentId);
        for (DoctorEntity doctor : doctors) {
            if (Integer.valueOf(1).equals(doctor.getStatus())) {
                doctor.setStatus(0);
                doctor.setUpdatedAt(TimeUtil.nowUtc());
                doctorMapper.updateById(doctor);
            }
        }
    }

    private DoctorEntity requireLocked(long id) {
        DoctorEntity doctor = doctorMapper.lockById(id);
        if (doctor == null) {
            throw BusinessException.missing("医生不存在");
        }
        return doctor;
    }

    private void validateUser(Long userId) {
        if (userId != null) {
            userService.requireEnabledDoctorUser(userId);
        }
    }

    private void copy(DoctorSaveDTO input, DoctorEntity doctor) {
        doctor.setUserId(input.userId());
        doctor.setDepartmentId(input.departmentId());
        doctor.setName(input.name().trim());
        doctor.setTitle(input.title());
        doctor.setSpecialty(input.specialty());
        doctor.setIntroduction(input.introduction());
        doctor.setAvatarUrl(input.avatarUrl());
        doctor.setStatus(input.status());
    }

    private DoctorVO toVO(DoctorEntity doctor, Map<Long, String> names) {
        return new DoctorVO(doctor.getId(), doctor.getUserId(), doctor.getDepartmentId(),
                names.getOrDefault(doctor.getDepartmentId(), "已删除科室"), doctor.getName(),
                doctor.getTitle(), doctor.getSpecialty(), doctor.getIntroduction(),
                doctor.getAvatarUrl(), doctor.getStatus());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void checkPage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw BusinessException.bad("分页参数无效");
        }
    }
}
